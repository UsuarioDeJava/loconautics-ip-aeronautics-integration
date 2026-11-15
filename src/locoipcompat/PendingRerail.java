package locoipcompat;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.teleport.TrainTeleporter;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trens do Loconautics levados pelo Immersive Portals: Aeronautics.
 * 1) Enquanto o IP move a nave de um mundo para outro, ela fica marcada como "em travessia", senão o
 *    Loconautics (SubLevelSplitWatcher) vê a nave ser removida do mundo de origem e apaga o trem.
 * 2) Depois da mudança, o vagão é assentado no trilho do mundo novo NO LUGAR em que está (sem recriar a nave,
 *    o que fazia ela sumir para quem olhava pelo portal), mantendo velocidade, acelerador e sentido de marcha.
 */
public final class PendingRerail {
    private static final Logger LOG = LoggerFactory.getLogger("LocoIpCompat");
    /** id da nave -> tick até quando conta como "em travessia" (Long.MAX_VALUE enquanto o IP está movendo) */
    private static final Map<UUID, Long> IN_FLIGHT = new ConcurrentHashMap<>();
    /** id da nave -> trem capturado antes da mudança */
    private static final Map<UUID, SableTrain> CAPTURED = new ConcurrentHashMap<>();
    /** id da nave -> bloco central do plot antes da mudança (os truques guardam posição relativa a ele) */
    private static final Map<UUID, BlockPos> OLD_CENTER = new ConcurrentHashMap<>();
    /** id do vagão -> estado de marcha antes da travessia */
    private static final Map<UUID, Before> BEFORE = new ConcurrentHashMap<>();
    /** id do vagão -> tick em que chegou ao mundo novo */
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();
    /** id do vagão -> último motivo de falha já registrado no log (para não repetir a cada tentativa) */
    private static final Map<UUID, String> LAST_WHY = new ConcurrentHashMap<>();
    private static final long IN_FLIGHT_GRACE = 60;  // 3 s depois da mudança
    private static final long SETTLE_TICKS = 1;
    private static final long RETRY_EVERY = 2;
    private static final long GIVE_UP_AFTER = 200;   // 10 s

    /** O que o vagão tinha antes de atravessar. */
    private record Before(RailCarriage carriage, double spacing, double speed, double target,
                          Vector3d localTangent, SableTrain.StationState state, GlobalStation station,
                          double distance, int dwell) {
    }

    private static Method findRailBelow;
    private static Method hitLocation;
    private static Method hitUpNormal;
    private static Method releaseContacts;
    private static boolean reflectionFailed;
    private static volatile long lastTick;

    private PendingRerail() {
    }

    // ---------------------------------------------------------------- chamado pelos mixins

    public static boolean isInFlight(UUID subLevelId) {
        if (subLevelId == null) {
            return false;
        }
        Long until = IN_FLIGHT.get(subLevelId);
        return until != null && until >= lastTick;
    }

    /**
     * As rodas fantasmas do Loconautics são naves à parte presas ao vagão, então o IP Aero as leva junto no mesmo
     * grupo. Quando a roda passa antes do vagão, ela chega do outro lado solta (o Loconautics já não sabe dela) e
     * fica embaixo do vagão, que bate nela e é arremessado. Por isso a roda nunca atravessa: é removida aqui, junto
     * com as outras rodas do mesmo vagão, e o Loconautics monta rodas novas quando o vagão chega.
     *
     * @return true quando a nave era uma roda fantasma e a mudança deve ser cancelada
     */
    public static boolean discardWheelBody(ServerSubLevel sub, ServerLevel srcLevel) {
        try {
            UUID id = sub.getUniqueId();
            UUID carId = GhostWheelBodies.parentCarOf(id);
            if (carId == null && !GhostWheelBodies.isWheelBody(id)) {
                return false;
            }
            SableTrain train = carId == null ? null : SableTrainRegistry.bySubLevel(carId);
            if (train != null) {
                GhostWheelBodies.releaseAll(train);
            }
            if (!sub.isRemoved()) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(srcLevel);
                if (container != null) {
                    sub.getPlot().destroyAllBlocks();
                    container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
                }
            }
            LOG.info("[LocoIP] roda fantasma {} do vagão {} ficou deste lado do portal (o Loconautics monta outra lá)",
                    shortId(id), shortId(carId));
            return true;
        } catch (Throwable t) {
            LOG.error("[LocoIP] falha ao descartar a roda fantasma {}", shortId(sub.getUniqueId()), t);
            return false;
        }
    }

    /** Antes do IP mover a nave: guarda o trem e marca a nave como em travessia. */
    public static void beforeMove(ServerSubLevel sub) {
        try {
            UUID id = sub.getUniqueId();
            SableTrain train = SableTrainRegistry.bySubLevel(id);
            if (train == null) {
                return; // não é vagão do Loconautics
            }
            CAPTURED.put(id, train);
            IN_FLIGHT.put(id, Long.MAX_VALUE);
            OLD_CENTER.put(id, sub.getPlot().getCenterBlock().immutable());
            BEFORE.put(train.id(), capture(train, sub));
            // como no teletransporte do próprio Loconautics: solta as rodas fantasmas, o estado do driver e as
            // restrições presas ao mundo de origem (senão: "Constraint validation failed" do outro lado)
            GhostWheelBodies.releaseAll(train);
            SableTrainDriver.forgetBodyState(train);
            if (reflect() && releaseContacts != null) {
                releaseContacts.invoke(null, sub);
            }
        } catch (Throwable t) {
            LOG.error("[LocoIP] falha antes da travessia", t);
        }
    }

    private static Before capture(SableTrain train, ServerSubLevel sub) {
        SableTrain.Car car = train.car();
        RailCarriage carriage = car == null ? null : car.carriage();
        double spacing = 1.0;
        if (carriage != null) {
            spacing = carriage.bogeySpacing();
        } else if (car != null && car.localLead() != null && car.localTrail() != null) {
            spacing = Math.max(1.0, car.localLead().distance(car.localTrail()));
        }
        Vector3d localTangent = null;
        try {
            if (car != null) {
                Vector3d tangent = SableTrainDriver.railTangent(car);
                localTangent = new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(new Vector3d(tangent));
            }
        } catch (Throwable ignored) {
            // sem trilho válido agora: o sentido é decidido pelo movimento depois
        }
        return new Before(carriage, spacing, train.speed(), train.targetSpeed(), localTangent,
                train.stationState(), train.currentStation(), train.distanceToStation(), train.dwellTicks());
    }

    /** Depois do IP mover a nave (newSub == null quando o IP recusou a travessia). */
    public static void afterMove(UUID oldId, ServerSubLevel newSub, ServerLevel dst, BlockPos shift) {
        SableTrain train = CAPTURED.remove(oldId);
        BlockPos oldCenter = OLD_CENTER.remove(oldId);
        if (train == null) {
            IN_FLIGHT.remove(oldId);
            return;
        }
        boolean moved = newSub != null;
        UUID newId = moved ? newSub.getUniqueId() : oldId;
        long now = dst.getServer().getTickCount();
        IN_FLIGHT.put(oldId, now + IN_FLIGHT_GRACE);
        if (!newId.equals(oldId)) {
            IN_FLIGHT.put(newId, now + IN_FLIGHT_GRACE);
        }
        if (!moved) {
            // o IP recusou a travessia: o vagão continua deste lado, então devolve as rodas fantasmas soltas antes
            BEFORE.remove(train.id());
            try {
                GhostWheelBodies.rebuild(train);
            } catch (Throwable t) {
                LOG.error("[LocoIP] falha ao devolver as rodas do trem {}", shortId(train.id()), t);
            }
            return;
        }
        try {
            if (SableTrainRegistry.get(train.id()) == null) {
                SableTrainRegistry.register(train);
                LOG.info("[LocoIP] o Loconautics tinha soltado o trem {}; registrado de novo", shortId(train.id()));
            }
            if (train.level() != dst) {
                train.setLevel(dst);
            }
            // o IP Aero desloca as coordenadas internas da nave (novo = antigo + shift). O "lead"/"trail" do vagão
            // são coordenadas internas absolutas (andam o shift inteiro); os truques são relativos ao bloco central
            // do plot, que também muda (X/Z junto com o plot, Y pela altura do mundo novo)
            BlockPos d = shift == null ? BlockPos.ZERO : shift;
            BlockPos newCenter = newSub.getPlot().getCenterBlock();
            BlockPos bogeyDelta = oldCenter == null ? BlockPos.ZERO : d.subtract(newCenter.subtract(oldCenter));
            shiftCar(train, newId, d, bogeyDelta);
            train.setDerailedQuiet(true); // até ser assentado; sem isso o driver tenta usar o trilho velho
            GhostWheelBodies.rebuild(train);
            PENDING.putIfAbsent(train.id(), now);
            LAST_WHY.remove(train.id());
            LOG.info("[LocoIP] vagão {} levado pelo Immersive Portals para {}; assentar no trilho agendado",
                    shortId(newId), dst.dimension().location());
        } catch (Throwable t) {
            LOG.error("[LocoIP] falha ao registrar a travessia do trem {}", shortId(train.id()), t);
        }
    }

    // ---------------------------------------------------------------- tick do servidor

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        lastTick = now;
        if (!IN_FLIGHT.isEmpty()) {
            IN_FLIGHT.values().removeIf(until -> until != Long.MAX_VALUE && until < now);
        }
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            long age = now - entry.getValue();
            if (age < SETTLE_TICKS || ((age - SETTLE_TICKS) % RETRY_EVERY) != 0) {
                continue;
            }
            UUID id = entry.getKey();
            SableTrain car = SableTrainRegistry.get(id);
            if (car == null) {
                forget(id);
                it.remove();
                continue;
            }
            try {
                String why = seatInPlace(car);
                if (why == null) {
                    forget(id);
                    it.remove();
                } else if (age >= GIVE_UP_AFTER) {
                    // deixa o re-encaixe automático do próprio Loconautics assumir quando o trem parar
                    car.setDerailedQuiet(true);
                    forget(id);
                    it.remove();
                    LOG.warn("[LocoIP] não consegui assentar o vagão {} no trilho ({}); fica descarrilado para o "
                            + "re-encaixe automático do Loconautics", shortId(id), why);
                } else if (!why.equals(LAST_WHY.put(id, why))) {
                    LOG.info("[LocoIP] vagão {} ainda não voltou ao trilho: {}", shortId(id), why);
                }
            } catch (Throwable t) {
                LOG.error("[LocoIP] erro assentando o vagão {} no trilho", shortId(id), t);
                forget(id);
                it.remove();
            }
        }
    }

    private static void forget(UUID trainId) {
        LAST_WHY.remove(trainId);
        BEFORE.remove(trainId);
    }

    /**
     * Assenta o vagão no trilho que está embaixo dele, sem tirar a nave do mundo (o teletransporte do
     * Loconautics remove e recarrega a nave, e quem olha pelo portal a vê sumir).
     *
     * @return null quando deu certo, ou o motivo de ainda não ter dado
     */
    private static String seatInPlace(SableTrain car) throws Exception {
        ServerLevel level = car.level();
        SableTrain.Car c = car.car();
        if (c == null) {
            return "vagão sem dados";
        }
        Long until = IN_FLIGHT.get(c.subLevelId());
        if (until != null && until == Long.MAX_VALUE) {
            return "o vagão ainda está sendo movido";
        }
        ServerSubLevel sub = SubLevelData.resolve(level, c.subLevelId());
        if (sub == null) {
            return "o vagão ainda não apareceu no mundo novo";
        }
        if (c.bogeys().isEmpty()) {
            return "o vagão está sem truques";
        }
        Before before = BEFORE.get(car.id());
        Vector3d anchor = SableTrainSpawner.bogeyAnchorWorld(sub, c);
        Vec3 facing = SableTrainSpawner.currentFacing(sub, c);
        Object hit = findRailBelow(level, BlockPos.containing(anchor.x, anchor.y, anchor.z), facing);
        if (hit == null) {
            return "nenhum trilho embaixo do vagão";
        }
        TrackGraphLocation loc = (TrackGraphLocation) hitLocation.invoke(hit);
        Vec3 up = (Vec3) hitUpNormal.invoke(hit);

        // um carriage provisório no trilho novo com o referencial do antigo, para o assento não virar o vagão 180°
        double spacing = before != null ? before.spacing() : 1.0;
        RailCarriage provisional = RailCarriage.at(loc, up, spacing, 0.0);
        if (provisional != null) {
            if (before != null && before.carriage() != null) {
                provisional.adoptReferenceFrame(before.carriage());
            }
            car.replaceCar(new SableTrain.Car(c.subLevelId(), provisional, c.bogeys(), c.localLead(), c.localTrail()));
        }

        SableTrainSpawner.SeatPose seat = SableTrainSpawner.seatPoseAt(level, car, loc, up, facing, true);
        if (seat == null || seat.origin() == null) {
            return "o trilho embaixo não dá uma posição para o vagão";
        }

        SeatBlend.begin(sub, seat.origin(), seat.orient());
        SableTrainSpawner.writePose(level, sub, seat.origin(), seat.orient());
        car.relocate(seat.carriage(), seat.bogeys()); // zera velocidade, acelerador e estado de estação
        car.setDerailedQuiet(false);

        double sign = 1.0;
        try {
            if (before != null) {
                sign = travelSign(before, sub, seat.orient(), car.car());
            }
        } catch (Throwable t) {
            LOG.warn("[LocoIP] não deu para conferir o sentido de marcha do vagão {}: {}", shortId(car.id()), t.toString());
        }
        if (before != null) {
            car.setSpeed(sign * before.speed());
            car.setTargetSpeed(sign * before.target());
            if (before.state() == SableTrain.StationState.STOPPED) {
                car.keepStop(before.state(), before.station(), before.distance(), before.dwell());
            }
        }
        car.beginPoseSettle(20);
        GhostWheelBodies.rebuild(car);
        SableTrainPersistence.persist(car);
        SableTrainSyncPacket.broadcast(car);
        LOG.info("[LocoIP] vagão {} assentado no trilho em {} ({} truque(s), velocidade {}, acelerador {}{})",
                shortId(car.id()), level.dimension().location(), seat.bogeys().size(),
                fmt(car.speed() * 20.0), fmt(car.targetSpeed() * 20.0), sign < 0 ? ", referencial do trilho invertido" : "");
        return null;
    }

    /**
     * A velocidade do Loconautics é medida ao longo da tangente do trilho do vagão, cujo sentido pode ter mudado
     * no assento novo. Devolve -1 quando o sentido mudou (velocidade e acelerador trocam de sinal) e +1 quando não.
     */
    private static double travelSign(Before before, ServerSubLevel sub, Quaterniond newOrient, SableTrain.Car newCar) {
        Vector3d tangent;
        try {
            tangent = SableTrainDriver.railTangent(newCar);
        } catch (Throwable t) {
            return 1.0;
        }
        // 1) pelo movimento: para onde a nave está andando agora, ao longo do trilho novo
        if (Math.abs(before.speed()) > 0.01) {
            RigidBodyHandle body = RigidBodyHandle.of(sub);
            if (body != null) {
                Vector3d v = body.getLinearVelocity(new Vector3d());
                double along = v.dot(tangent) / 20.0;
                if (Math.abs(along) > 0.005) {
                    return Math.signum(along) * Math.signum(before.speed());
                }
            }
        }
        // 2) pelo próprio vagão: a tangente vista de dentro da nave, antes e depois
        if (before.localTangent() != null) {
            Vector3d localNow = new Quaterniond(newOrient).conjugate().transform(new Vector3d(tangent));
            double dot = localNow.dot(before.localTangent());
            if (Math.abs(dot) > 0.3) {
                return Math.signum(dot);
            }
        }
        return 1.0;
    }

    private static void shiftCar(SableTrain train, UUID newId, BlockPos d, BlockPos bogeyDelta) {
        SableTrain.Car old = train.car();
        if (old == null) {
            return;
        }
        List<SableTrain.Bogey> bogeys = new ArrayList<>();
        for (SableTrain.Bogey b : old.bogeys()) {
            bogeys.add(new SableTrain.Bogey(b.localPos().offset(bogeyDelta), null));
        }
        Vector3dc lead = old.localLead() == null ? null : new Vector3d(old.localLead()).add(d.getX(), d.getY(), d.getZ());
        Vector3dc trail = old.localTrail() == null ? null : new Vector3d(old.localTrail()).add(d.getX(), d.getY(), d.getZ());
        UUID id = newId != null ? newId : old.subLevelId();
        train.replaceCar(new SableTrain.Car(id, null, bogeys, lead, trail));
    }

    private static Object findRailBelow(ServerLevel level, BlockPos pos, Vec3 look) throws Exception {
        if (!reflect()) {
            throw new IllegalStateException("reflexão no Loconautics indisponível");
        }
        return findRailBelow.invoke(null, level, pos, look);
    }

    private static synchronized boolean reflect() {
        if (findRailBelow != null) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Method find = SableTrainSpawner.class.getDeclaredMethod("findRailBelow", ServerLevel.class, BlockPos.class, Vec3.class);
            find.setAccessible(true);
            Class<?> hitClass = find.getReturnType();
            hitLocation = hitClass.getDeclaredMethod("location");
            hitLocation.setAccessible(true);
            hitUpNormal = hitClass.getDeclaredMethod("upNormal");
            hitUpNormal.setAccessible(true);
            try {
                releaseContacts = TrainTeleporter.class.getDeclaredMethod("releaseContacts", ServerSubLevel.class);
                releaseContacts.setAccessible(true);
            } catch (Throwable t) {
                releaseContacts = null; // opcional
            }
            findRailBelow = find;
            return true;
        } catch (Throwable t) {
            reflectionFailed = true;
            LOG.error("[LocoIP] a versão do Loconautics mudou e a integração não achou os métodos internos", t);
            return false;
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f b/s", v);
    }

    private static String shortId(UUID id) {
        return id == null ? "?" : id.toString().substring(0, 8);
    }
}

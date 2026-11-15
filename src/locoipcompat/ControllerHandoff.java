package locoipcompat;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.network.LoconauticsNetwork;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controle manual (Analog Controller do Loconautics) atravessando portais do IP Aero.
 * - Quando o IP muda a nave de mundo, ele recarrega os blocos a partir do NBT, e o controlador volta sem "usuário"
 *   (o Loconautics não guarda isso no NBT). Aqui o mesmo jogador é devolvido ao controlador do outro lado, com o
 *   estado das teclas, e o cliente recebe a posição nova do controlador.
 * - Enquanto o jogador e o trem estão em mundos diferentes (um já passou pelo portal, o outro ainda não), o
 *   controlador não solta o jogador por alguns segundos.
 */
public final class ControllerHandoff {
    private static final Logger LOG = LoggerFactory.getLogger("LocoIpCompat");
    /** ticks que o controlador espera o jogador e o trem ficarem no mesmo mundo de novo */
    private static final long AWAY_GRACE = 100;
    private static final String[] KEPT = {"driveInput", "steerInput", "raisingSignal", "loweringSignal", "spaceSignal", "lockSignal"};

    private record Held(BlockPos pos, UUID user, Map<String, Object> fields) {
    }

    /** id da nave -> controladores com usuário antes da mudança */
    private static final Map<UUID, List<Held>> HELD = new ConcurrentHashMap<>();
    /** jogador -> tick em que ficou em outro mundo que o controlador */
    private static final Map<UUID, Long> AWAY = new ConcurrentHashMap<>();

    private static Field userField;
    private static Field lastKeyField;
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static boolean reflected;
    private static boolean reflectionFailed;

    private ControllerHandoff() {
    }

    /** Substitui o "level.getPlayerByUUID(usuário)" do tick do controlador. */
    public static Player playerForController(Level level, UUID id) {
        Player here = level.getPlayerByUUID(id);
        if (here != null || id == null) {
            if (id != null) {
                AWAY.remove(id);
            }
            return here;
        }
        if (level.getServer() == null) {
            return null;
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(id);
        if (online == null) {
            AWAY.remove(id);
            return null;
        }
        long now = level.getServer().getTickCount();
        Long since = AWAY.putIfAbsent(id, now);
        if (since == null || now - since <= AWAY_GRACE) {
            return online; // atravessando o portal: segura o controle
        }
        AWAY.remove(id);
        return null; // ficou do outro lado tempo demais: o controlador solta o jogador
    }

    /** Antes do IP mover a nave: guarda quem estava em cada controlador dela. */
    public static void capture(ServerSubLevel sub) {
        try {
            List<Held> held = new ArrayList<>();
            for (AnalogControllerBlockEntity be : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                UUID user = be.getCurrentUser();
                if (user != null) {
                    held.add(new Held(be.getBlockPos().immutable(), user, readFields(be)));
                }
            }
            if (!held.isEmpty()) {
                HELD.put(sub.getUniqueId(), held);
            }
        } catch (Throwable t) {
            LOG.error("[LocoIP] falha ao guardar o controle manual da nave {}", sub.getUniqueId(), t);
        }
    }

    /** Depois do IP mover a nave (newSub == null quando o IP não conseguiu). */
    public static void restore(UUID oldId, ServerSubLevel newSub, ServerLevel src, ServerLevel dst, BlockPos shift) {
        List<Held> held = HELD.remove(oldId);
        if (held == null) {
            return;
        }
        ServerLevel level = newSub != null ? dst : src;
        BlockPos d = newSub != null && shift != null ? shift : BlockPos.ZERO;
        for (Held h : held) {
            try {
                BlockPos pos = h.pos().offset(d); // o IP desloca as posições dos blocos da nave pelo shift
                if (!(level.getBlockEntity(pos) instanceof AnalogControllerBlockEntity be)) {
                    LOG.warn("[LocoIP] não achei o controlador em {} depois da travessia", pos.toShortString());
                    continue;
                }
                if (be.getCurrentUser() != null) {
                    continue; // não foi recarregado (o IP não chegou a mover a nave)
                }
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(h.user());
                if (writeFields(be, h, level.getGameTime())) {
                    be.setChanged();
                    be.sendData();
                    if (player != null) {
                        LoconauticsNetwork.sendMount(player, true, pos);
                    }
                } else if (player != null) {
                    be.resumeUser(player); // reserva: devolve o controle, mas zera o acelerador
                }
                LOG.info("[LocoIP] controle manual em {} continua com {} do outro lado do portal", pos.toShortString(),
                        player != null ? player.getName().getString() : h.user());
            } catch (Throwable t) {
                LOG.error("[LocoIP] falha ao devolver o controle manual", t);
            }
        }
    }

    private static Map<String, Object> readFields(AnalogControllerBlockEntity be) {
        Map<String, Object> out = new HashMap<>();
        if (!reflect()) {
            return out;
        }
        for (Map.Entry<String, Field> e : FIELDS.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().get(be));
            } catch (Throwable ignored) {
                // campo opcional
            }
        }
        return out;
    }

    private static boolean writeFields(AnalogControllerBlockEntity be, Held h, long now) {
        if (!reflect()) {
            return false;
        }
        try {
            userField.set(be, h.user());
            if (lastKeyField != null) {
                lastKeyField.setLong(be, now);
            }
            for (Map.Entry<String, Object> e : h.fields().entrySet()) {
                Field f = FIELDS.get(e.getKey());
                if (f != null && e.getValue() != null) {
                    f.set(be, e.getValue());
                }
            }
            return true;
        } catch (Throwable t) {
            LOG.warn("[LocoIP] não deu para copiar o estado do controlador: {}", t.toString());
            return false;
        }
    }

    private static synchronized boolean reflect() {
        if (reflected) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Class<?> c = AnalogControllerBlockEntity.class;
            userField = c.getDeclaredField("currentUser");
            userField.setAccessible(true);
            try {
                lastKeyField = c.getDeclaredField("lastKeyTick");
                lastKeyField.setAccessible(true);
            } catch (Throwable t) {
                lastKeyField = null;
            }
            for (String name : KEPT) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    FIELDS.put(name, f);
                } catch (Throwable ignored) {
                    // a versão do Loconautics mudou esse campo: segue sem ele
                }
            }
            reflected = true;
            return true;
        } catch (Throwable t) {
            reflectionFailed = true;
            LOG.warn("[LocoIP] a versão do Loconautics mudou o controlador; o controle manual volta com o acelerador zerado");
            return false;
        }
    }
}

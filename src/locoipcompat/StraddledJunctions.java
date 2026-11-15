package locoipcompat;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.TurnMemory;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desvios que ficaram "entre" os truques depois da travessia.
 *
 * No Loconautics, o primeiro truque que passa por um desvio grava o lado escolhido na memória do trem
 * (TurnMemory) e os de trás seguem o mesmo lado. Sem nada gravado, quem chega escolhe pelo comando de direção
 * do jogador. O IP Aero entrega o trem do outro lado já um vagão inteiro depois do portal, então, se houver um
 * desvio colado na saída, o truque da frente já está depois dele sem nunca ter "passado" por ele. O de trás
 * chega, não acha nada gravado, segue o comando de virar e o vagão se parte.
 *
 * Aqui o caminho do truque mais de trás até o mais da frente é percorrido no trilho, e cada desvio encontrado é
 * gravado na memória do trem como se o truque da frente tivesse acabado de passar por ele.
 */
final class StraddledJunctions {
    private static final Logger LOG = LoggerFactory.getLogger("LocoIpCompat");
    private static Method memoryFor;
    private static boolean reflectionFailed;

    private record Bogeyed(SableTrain car, SableTrain.Bogey bogey, double along) {
    }

    private record Crossing(TrackEdge leaving, boolean forward, List<Map.Entry<TrackNode, TrackEdge>> options,
                            Map.Entry<TrackNode, TrackEdge> chosen) {
    }

    private StraddledJunctions() {
    }

    /** @param consist todos os vagões do trem, já assentados no trilho do mundo novo */
    static void register(List<SableTrain> consist, SableTrain reference) {
        // Enquanto a nave ainda atravessava, os trilhos "virtuais" do trem já tinham passado pelo portal do Create
        // e escolhido lados de desvios do outro lado (com o comando de virar daquele momento). Mas a nave chega
        // reta e os truques são assentados onde estão de verdade. Essas escolhas antigas não batem mais e fariam o
        // truque de trás ir para um lado diferente do da frente: são apagadas, e as válidas são gravadas abaixo.
        forgetEarlierChoices(consist, reference);
        try {
            Vector3d travel = travelDirection(reference);
            if (travel == null) {
                return; // parado: ninguém está prestes a passar por desvio nenhum
            }
            List<Bogeyed> bogeys = new ArrayList<>();
            for (SableTrain car : consist) {
                if (car.car() == null) {
                    continue;
                }
                for (SableTrain.Bogey b : car.car().bogeys()) {
                    Vector3d c = b.rail() == null ? null : b.rail().center();
                    if (c != null) {
                        bogeys.add(new Bogeyed(car, b, c.x * travel.x + c.z * travel.z));
                    }
                }
            }
            if (bogeys.size() < 2) {
                return;
            }
            // de trás para a frente, no sentido de marcha
            bogeys.sort((x, y) -> Double.compare(x.along(), y.along()));
            int recorded = 0;
            int failed = 0;
            TurnMemory memory = null;
            for (int i = 0; i + 1 < bogeys.size(); i++) {
                Bogeyed behind = bogeys.get(i);
                Bogeyed ahead = bogeys.get(i + 1);
                List<Crossing> crossings = walk(behind.bogey().rail(), ahead.bogey().rail(), travel);
                if (crossings == null) {
                    failed++;
                    continue;
                }
                if (crossings.isEmpty()) {
                    continue;
                }
                if (memory == null) {
                    memory = memory(ahead.car());
                    if (memory == null) {
                        return;
                    }
                }
                TurnMemory.BogeyId aheadId = new TurnMemory.BogeyId(ahead.car().car().subLevelId(), ahead.bogey().localPos());
                for (Crossing c : crossings) {
                    memory.settle(c.leaving(), c.forward(), c.options(), c.chosen(), null, aheadId);
                }
                recorded += crossings.size();
            }
            if (recorded > 0) {
                LOG.info("[LocoIP] {} desvio(s) entre os truques do trem {} gravado(s) com o lado do truque da frente",
                        recorded, shortId(reference.id()));
            }
            if (failed > 0) {
                LOG.info("[LocoIP] trem {}: em {} trecho(s) entre truques o caminho no trilho não fechou; nada gravado neles",
                        shortId(reference.id()), failed);
            }
        } catch (Throwable t) {
            LOG.warn("[LocoIP] não deu para conferir desvios entre os truques do trem {}: {}", shortId(reference.id()), t.toString());
        }
    }

    /**
     * Anda no trilho do truque de trás até o da frente, em passos curtos, anotando os desvios do caminho.
     *
     * @return os desvios (vazio se não há nenhum), ou null quando o caminho não chegou no truque da frente
     */
    private static List<Crossing> walk(RailCarriage behindRail, RailCarriage aheadRail, Vector3d travel) {
        TrackGraph graph = behindRail.graph();
        if (graph == null || graph != aheadRail.graph()) {
            return null;
        }
        // até a ponta da frente do truque da frente: se ele estiver bem em cima do nó de um desvio, o caminho
        // precisa passar pelo nó para o desvio ser gravado
        TravellingPoint start = mostAlong(graph, travel, behindRail.leading(), behindRail.trailing(), true);
        TravellingPoint target = mostAlong(graph, travel, aheadRail.leading(), aheadRail.trailing(), true);
        if (start == null || target == null) {
            return null;
        }
        Vec3 goal = target.getPosition(graph);
        double gap = start.getPosition(graph).distanceTo(goal);
        if (gap < 0.3) {
            return List.of();
        }
        // para que lado do trilho fica o truque da frente
        TravellingPoint probe = copy(start);
        probe.travel(graph, 0.25, probe.follow(target), probe.ignoreEdgePoints(), probe.ignoreTurns(), probe.ignorePortals());
        double sign = probe.getPosition(graph).distanceTo(goal) <= gap ? 1.0 : -1.0;

        TravellingPoint walker = copy(start);
        List<Crossing> crossings = new ArrayList<>();
        TravellingPoint.ITrackSelector follow = walker.follow(target);
        TravellingPoint.ITrackSelector recording = (g, pair) -> {
            Map.Entry<TrackNode, TrackEdge> chosen = follow.apply(g, pair);
            List<Map.Entry<TrackNode, TrackEdge>> options = pair.getSecond();
            if (options.size() >= 2 && walker.edge != null) {
                crossings.add(new Crossing(walker.edge, pair.getFirst(), new ArrayList<>(options), chosen));
            }
            return chosen;
        };
        double maxLen = gap * 1.6 + 2.0;
        double best = gap;
        double d = gap;
        for (double walked = 0; walked < maxLen && d > 0.6; walked += 0.5) {
            walker.travel(graph, sign * 0.5, recording, walker.ignoreEdgePoints(), walker.ignoreTurns(), walker.ignorePortals());
            d = walker.getPosition(graph).distanceTo(goal);
            if (d > best + 2.0) {
                break; // está se afastando: pegou o lado errado
            }
            best = Math.min(best, d);
        }
        return d <= 1.5 ? crossings : null;
    }

    /** Apaga a memória de desvios do trem e o registro de desvios de cada vagão (feitos antes da entrega). */
    private static void forgetEarlierChoices(List<SableTrain> consist, SableTrain reference) {
        try {
            TurnMemory memory = memory(reference);
            if (memory != null) {
                Field picks = TurnMemory.class.getDeclaredField("picks");
                picks.setAccessible(true);
                ((Map<?, ?>) picks.get(memory)).clear();
            }
            Class<?> advance = Class.forName("com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance");
            Field logs = advance.getDeclaredField("TURN_LOGS");
            logs.setAccessible(true);
            Map<?, ?> turnLogs = (Map<?, ?>) logs.get(null);
            for (SableTrain car : consist) {
                if (car.car() != null) {
                    turnLogs.remove(car.car().subLevelId());
                }
            }
        } catch (Throwable t) {
            LOG.warn("[LocoIP] não deu para apagar as escolhas de desvio antigas do trem {}: {}", shortId(reference.id()), t.toString());
        }
    }

    /** Sentido de marcha no mundo: tangente do trilho do vagão vezes o sinal da velocidade (ou do acelerador). */
    private static Vector3d travelDirection(SableTrain car) {
        double s = Math.abs(car.speed()) > 1.0E-3 ? Math.signum(car.speed()) : Math.signum(car.targetSpeed());
        if (s == 0 || car.car() == null) {
            return null;
        }
        Vector3d t = SableTrainDriver.railTangent(car.car());
        Vector3d flat = new Vector3d(t.x * s, 0, t.z * s);
        return flat.lengthSquared() < 1.0E-9 ? null : flat.normalize();
    }

    private static TravellingPoint mostAlong(TrackGraph graph, Vector3d dir, TravellingPoint p1, TravellingPoint p2, boolean max) {
        if (p1 == null || p1.edge == null) {
            return p2 == null || p2.edge == null ? null : copy(p2);
        }
        if (p2 == null || p2.edge == null) {
            return copy(p1);
        }
        Vec3 a = p1.getPosition(graph);
        Vec3 b = p2.getPosition(graph);
        double da = a.x * dir.x + a.z * dir.z;
        double db = b.x * dir.x + b.z * dir.z;
        return copy((da >= db) == max ? p1 : p2);
    }

    private static TravellingPoint copy(TravellingPoint p) {
        return new TravellingPoint(p.node1, p.node2, p.edge, p.position, p.upsideDown);
    }

    private static TurnMemory memory(SableTrain car) {
        synchronized (StraddledJunctions.class) {
            if (memoryFor == null && !reflectionFailed) {
                try {
                    Class<?> c = Class.forName("com.lycoris.loconautics.trainphysics.drive.ConsistTurns");
                    Method m = c.getDeclaredMethod("memoryFor", SableTrain.class);
                    m.setAccessible(true);
                    memoryFor = m;
                } catch (Throwable t) {
                    reflectionFailed = true;
                    LOG.warn("[LocoIP] a versão do Loconautics mudou: memória de desvios não encontrada ({})", t.toString());
                }
            }
        }
        if (memoryFor == null) {
            return null;
        }
        try {
            return (TurnMemory) memoryFor.invoke(null, car);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "?" : id.toString().substring(0, 8);
    }
}

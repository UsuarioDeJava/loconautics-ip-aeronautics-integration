package locoipcompat.mixin;

import com.lycoris.loconautics.trainphysics.rail.TurnMemory;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.List;
import java.util.Map;
import locoipcompat.LocoDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnóstico: cada consulta de um truque à memória de desvios do trem (só com "/locoip debug" ligado). */
@Mixin(value = TurnMemory.class, remap = false)
public abstract class TurnMemoryMixin {
    private static final Logger LOCOIP_LOG = LoggerFactory.getLogger("LocoIpCompat");

    @Inject(method = "settle", at = @At("RETURN"))
    private void locoip$traceSettle(TrackEdge leaving, boolean forward, List<Map.Entry<TrackNode, TrackEdge>> options,
                                    Map.Entry<TrackNode, TrackEdge> own, Map.Entry<TrackNode, TrackEdge> planned,
                                    TurnMemory.BogeyId id, CallbackInfoReturnable<Map.Entry<TrackNode, TrackEdge>> cir) {
        if (!LocoDebug.enabled()) {
            return;
        }
        try {
            Map.Entry<TrackNode, TrackEdge> result = cir.getReturnValue();
            LOCOIP_LOG.info("[LocoIP-debug] desvio {}>{} fwd={} truque={}/{} opções={} próprio={} planejado={} => {}{}",
                    loc(leaving.node1), loc(leaving.node2), forward,
                    id == null || id.car() == null ? "?" : id.car().toString().substring(0, 8),
                    id == null || id.bogey() == null ? "vão" : id.bogey().toShortString(),
                    options.size(), node(own), node(planned), node(result),
                    result != null && own != null && result.getKey() != own.getKey() ? " (memória mudou a escolha)" : "");
        } catch (Throwable ignored) {
            // diagnóstico não pode atrapalhar o jogo
        }
    }

    private static String node(Map.Entry<TrackNode, TrackEdge> e) {
        return e == null ? "-" : loc(e.getKey());
    }

    private static String loc(TrackNode n) {
        if (n == null) {
            return "?";
        }
        var l = n.getLocation().getLocation();
        return String.format(java.util.Locale.ROOT, "(%.0f,%.0f,%.0f)", l.x, l.y, l.z);
    }
}

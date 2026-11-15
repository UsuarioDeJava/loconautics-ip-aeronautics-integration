package locoipcompat;

import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Field;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnóstico: "/locoip debug" liga e desliga o registro detalhado do Loconautics (que vem bloqueado na versão
 * publicada) e o rastreio de desvios dele, para ver no log como cada truque escolheu o lado.
 */
public final class LocoDebug {
    private static final Logger LOG = LoggerFactory.getLogger("LocoIpCompat");
    private static volatile boolean enabled;

    private LocoDebug() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("locoip")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug").executes(ctx -> {
                    enabled = !enabled;
                    setSteerTrace(enabled);
                    LOG.info("[LocoIP] diagnóstico de desvios {}", enabled ? "LIGADO" : "desligado");
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Loconautics x IP: diagnóstico de desvios " + (enabled ? "ligado" : "desligado")), false);
                    return 1;
                })));
    }

    private static void setSteerTrace(boolean on) {
        try {
            Class<?> c = Class.forName("com.lycoris.loconautics.foundation.debug.SteerTrace");
            Field f = c.getDeclaredField("enabled");
            f.setAccessible(true);
            f.setBoolean(null, on);
        } catch (Throwable t) {
            LOG.warn("[LocoIP] não deu para ligar o rastreio de desvios do Loconautics: {}", t.toString());
        }
    }
}

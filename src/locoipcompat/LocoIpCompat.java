package locoipcompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Integração Loconautics x Immersive Portals: Aeronautics.
 * Com o IP Aero instalado, a travessia de portal do próprio Loconautics (gêmeo + corte) é desligada:
 * o IP Aero leva o trem pelo portal (straddle) e este mod recoloca o trem no trilho do outro lado.
 */
@Mod(LocoIpCompat.MODID)
public final class LocoIpCompat {
    public static final String MODID = "loconautics_ip_compat";

    public LocoIpCompat(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> PendingRerail.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LocoDebug.register(event.getDispatcher()));
    }
}

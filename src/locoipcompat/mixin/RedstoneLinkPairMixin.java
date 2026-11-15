package locoipcompat.mixin;

import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import locoipcompat.LinkRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Receptores de Redstone Link do Loconautics (Bearing Axle e afins) dentro de naves do Sable.
 * Quando o receptor se registra de novo (ao carregar, ou quando a nave atravessa um portal e é recriada), o
 * Loconautics busca o sinal atual com o alcance "cru": compara a coordenada interna da nave (milhões de blocos
 * longe) com a do transmissor no chão e acha que está fora de alcance. Uma alavanca que já estava ligada deixa
 * de valer e o trem não vira. Aqui o alcance é calculado com a posição real da nave, como o Sable faz no Create.
 */
@Mixin(value = RedstoneLinkPair.class, remap = false)
public abstract class RedstoneLinkPairMixin {
    @Redirect(method = "deliverCurrent", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"))
    private static boolean locoip$rangeFromRealPosition(IRedstoneLinkable receiver, IRedstoneLinkable other) {
        Boolean projected = LinkRange.withinRange(receiver, other);
        return projected != null ? projected : RedstoneLinkNetworkHandler.withinRange(receiver, other);
    }
}

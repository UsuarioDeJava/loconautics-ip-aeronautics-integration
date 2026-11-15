package locoipcompat.mixin;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import locoipcompat.client.ClientControllerHandoff;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Controle manual do Loconautics no cliente: não larga o controle quando o trem atravessa o portal. */
@Mixin(value = AnalogControllerClientHandler.class, remap = false)
public abstract class AnalogControllerClientMixin {
    @Shadow
    private static BlockPos mountedPos;
    @Shadow
    private static int packetCooldown;

    /** Já controlando e o servidor manda a posição nova do controlador: troca em silêncio, sem soltar as teclas. */
    @Inject(method = "startControlling", at = @At("HEAD"), cancellable = true)
    private static void locoip$swapPosition(BlockPos pos, CallbackInfo ci) {
        if (mountedPos != null && pos != null) {
            mountedPos = pos.immutable();
            packetCooldown = 0;
            ClientControllerHandoff.onSwap();
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void locoip$holdThroughPortal(CallbackInfo ci) {
        BlockPos result = ClientControllerHandoff.check(mountedPos);
        if (result == ClientControllerHandoff.HOLD) {
            ci.cancel();
        } else if (result != null) {
            mountedPos = result;
            packetCooldown = 0;
        }
    }
}

package locoipcompat.mixin;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing;
import java.util.UUID;
import locoipcompat.PendingRerail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrainPortalCrossing.class, remap = false)
public abstract class TrainPortalCrossingMixin {
    /** Desliga a travessia própria do Loconautics (gêmeo + corte): quem leva o trem pelo portal é o IP Aero. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void locoip$letImmersivePortalsCarry(SableTrain train, CallbackInfo ci) {
        ci.cancel();
    }

    /** Enquanto o IP Aero move um vagão, o Loconautics deve tratar como travessia (e não apagar o trem). */
    @Inject(method = "isCrossing", at = @At("HEAD"), cancellable = true)
    private static void locoip$movedByImmersivePortals(UUID subLevelId, CallbackInfoReturnable<Boolean> cir) {
        if (PendingRerail.isInFlight(subLevelId)) {
            cir.setReturnValue(true);
        }
    }
}

package locoipcompat.mixin;

import com.lycoris.loconautics.config.DiagnosticsConfig;
import locoipcompat.LocoDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Com "/locoip debug" ligado, deixa passar o registro detalhado do Loconautics. */
@Mixin(value = DiagnosticsConfig.class, remap = false)
public abstract class DiagnosticsConfigMixin {
    @Inject(method = "debugOutput", at = @At("HEAD"), cancellable = true)
    private static void locoip$debugWhenAsked(CallbackInfoReturnable<Boolean> cir) {
        if (LocoDebug.enabled()) {
            cir.setReturnValue(true);
        }
    }
}

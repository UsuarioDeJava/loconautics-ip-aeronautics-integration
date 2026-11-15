package locoipcompat.mixin;

import java.util.Map;
import java.util.UUID;
import locoipcompat.KnuckleHandoff;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.sable_integration.IPShipJoints;

/**
 * O IP Aero chama shiftLoadedJoints uma vez depois de mover um grupo de naves amarradas, com o deslocamento de
 * cada uma: é a hora de acertar os engates do Loconautics.
 */
@Mixin(value = IPShipJoints.class, remap = false)
public abstract class IPShipJointsMixin {
    @Inject(method = "shiftLoadedJoints", at = @At("HEAD"))
    private static void locoip$followKnuckles(ServerLevel level, Map<UUID, BlockPos> shifts, CallbackInfo ci) {
        KnuckleHandoff.followPartners(level, shifts);
    }
}

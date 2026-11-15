package locoipcompat.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Set;
import java.util.UUID;
import locoipcompat.ControllerHandoff;
import locoipcompat.KnuckleHandoff;
import locoipcompat.PendingRerail;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge;

/** Acompanha o IP Aero levando uma nave (ou vagão) de um mundo para outro. */
@Mixin(value = IPSableBridge.class, remap = false)
public abstract class IPSableBridgeMixin {
    private static final String MOVE =
        "moveThroughPortal(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge$Velocity;Ljava/util/Set;Z)Lqouteall/imm_ptl/core/compat/sable_integration/IPSableBridge$Moved;";

    @Inject(method = MOVE, at = @At("HEAD"), cancellable = true)
    private static void locoip$beforeMove(ServerSubLevel sub, ServerLevel srcLevel, ServerLevel dstLevel, Vec3 destPos,
                                          Quaterniond rotation, IPSableBridge.Velocity carried, Set<UUID> travelling,
                                          boolean straddled, CallbackInfoReturnable<IPSableBridge.Moved> cir) {
        // roda fantasma do Loconautics: não atravessa; é descartada e o Loconautics cria outra do lado de lá
        if (PendingRerail.discardWheelBody(sub, srcLevel)) {
            cir.setReturnValue(null);
            return;
        }
        KnuckleHandoff.setMoving(sub.getUniqueId(), true);
        ControllerHandoff.capture(sub);
        PendingRerail.beforeMove(sub);
    }

    @Inject(method = MOVE, at = @At("RETURN"))
    private static void locoip$afterMove(ServerSubLevel sub, ServerLevel srcLevel, ServerLevel dstLevel, Vec3 destPos,
                                         Quaterniond rotation, IPSableBridge.Velocity carried, Set<UUID> travelling,
                                         boolean straddled, CallbackInfoReturnable<IPSableBridge.Moved> cir) {
        IPSableBridge.Moved moved = cir.getReturnValue();
        KnuckleHandoff.setMoving(sub.getUniqueId(), false);
        PendingRerail.afterMove(sub.getUniqueId(), moved != null ? moved.sub() : null, dstLevel,
                moved != null ? moved.shift() : null);
        ControllerHandoff.restore(sub.getUniqueId(), moved != null ? moved.sub() : null, srcLevel, dstLevel,
                moved != null ? moved.shift() : null);
    }
}

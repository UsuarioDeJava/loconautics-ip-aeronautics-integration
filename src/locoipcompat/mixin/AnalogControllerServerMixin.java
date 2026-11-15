package locoipcompat.mixin;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import java.util.UUID;
import locoipcompat.ControllerHandoff;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** O controlador não solta o jogador só porque ele (ou o trem) já passou para o outro mundo pelo portal. */
@Mixin(value = AnalogControllerBlockEntity.class, remap = false)
public abstract class AnalogControllerServerMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"))
    private Player locoip$keepUserAcrossPortal(Level level, UUID id) {
        return ControllerHandoff.playerForController(level, id);
    }
}

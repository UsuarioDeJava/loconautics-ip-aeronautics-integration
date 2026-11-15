package locoipcompat.mixin;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Comando de virar sem ninguém no controle (alavanca de painel no trem, Redstone Link, redstone no eixo).
 *
 * O Loconautics só põe a intenção de virar na memória de desvios do trem quando o vagão tem "motorista" (alguém no
 * Analog Controller ou horário). Sem motorista, quem chega primeiro ao desvio decide; se for o vão do vagão (que
 * não sabe virar), ele grava "reto" e o truque da frente, que queria virar, é obrigado a seguir reto também.
 * Aqui um vagão cujo Bearing Axle está recebendo comando de virar também conta como quem dirige.
 */
@Mixin(targets = "com.lycoris.loconautics.trainphysics.drive.ConsistTurns", remap = false)
public abstract class ConsistTurnsMixin {
    @Inject(method = "isDriver", at = @At("RETURN"), cancellable = true)
    private static void locoip$steeredWithoutDriver(SableTrain train, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || train == null || train.isGhost() || train.isDerailed() || train.isPhysics()
                || train.car() == null) {
            return;
        }
        try {
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null) {
                return;
            }
            for (BearingAxleBlockEntity axle : SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class)) {
                if (axle.getSteerInput() != 0) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // sem eixo ou estrutura diferente: fica como o Loconautics decidiu
        }
    }
}

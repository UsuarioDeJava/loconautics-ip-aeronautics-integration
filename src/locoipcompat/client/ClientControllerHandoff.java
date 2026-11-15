package locoipcompat.client;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import com.simibubi.create.foundation.utility.ControlsUtil;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Lado do cliente do controle manual atravessando portais: quando a nave muda de mundo, o controlador muda de
 * posição (o IP Aero desloca as coordenadas internas da nave). O Loconautics guarda só a posição antiga, não acha
 * mais o controlador e larga o controle, devolvendo as teclas ao jogador (o W segurado faz ele sair andando).
 * Aqui a posição é reencontrada na nave, e durante a travessia o controle fica segurado em vez de largado.
 */
public final class ClientControllerHandoff {
    /** devolvido para "segurar": o tick do Loconautics não roda e as teclas ficam presas */
    public static final BlockPos HOLD = new BlockPos(0, Integer.MIN_VALUE, 0);
    private static final int GRACE = 100;

    private static UUID shipId;
    private static int dx;
    private static int dy;
    private static int dz;
    private static int lostTicks;
    private static ClientLevel lastLevel;
    private static int levelChangedAgo = GRACE;

    private ClientControllerHandoff() {
    }

    /**
     * @return null para seguir normal, uma posição nova para o controlador, ou {@link #HOLD} para segurar o controle
     */
    public static BlockPos check(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            levelChangedAgo = 0;
        } else if (levelChangedAgo < GRACE) {
            levelChangedAgo++;
        }
        if (pos == null || mc.level == null) {
            lostTicks = 0;
            return null;
        }
        if (AnalogControllerClientHandler.resolveMountedBlockEntity() != null
                || mc.level.getBlockState(pos).getBlock() instanceof AnalogControllerBlock) {
            remember(pos);
            lostTicks = 0;
            return null;
        }
        BlockPos moved = remap(mc.level, pos);
        if (moved != null) {
            lostTicks = 0;
            return moved;
        }
        boolean crossing = levelChangedAgo < GRACE || (shipId != null && ship(mc.level) == null);
        if (crossing && lostTicks < GRACE) {
            lostTicks++;
            ControlsUtil.getControls().forEach(kb -> kb.setDown(false));
            return HOLD;
        }
        lostTicks = 0;
        return null; // o Loconautics larga o controle normalmente
    }

    /** A posição mudou por causa do servidor (o controle continua do outro lado): só zera a espera. */
    public static void onSwap() {
        lostTicks = 0;
    }

    private static void remember(BlockPos pos) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(pos);
        if (sub == null) {
            shipId = null;
            return;
        }
        shipId = sub.getUniqueId();
        BlockPos c = sub.getPlot().getCenterBlock();
        dx = pos.getX() - c.getX();
        dy = pos.getY() - c.getY();
        dz = pos.getZ() - c.getZ();
    }

    private static SubLevel ship(ClientLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null || shipId == null ? null : container.getSubLevel(shipId);
    }

    /** A mesma nave apareceu com outras coordenadas internas: acha o controlador de novo (X/Z em relação ao centro não mudam). */
    private static BlockPos remap(ClientLevel level, BlockPos current) {
        SubLevel sub = ship(level);
        if (sub == null) {
            return null;
        }
        BlockPos c = sub.getPlot().getCenterBlock();
        BlockPos best = null;
        int bestDy = Integer.MAX_VALUE;
        for (AnalogControllerBlockEntity be : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            BlockPos p = be.getBlockPos();
            if (p.getX() - c.getX() == dx && p.getZ() - c.getZ() == dz) {
                int diff = Math.abs((p.getY() - c.getY()) - dy);
                if (diff < bestDy) {
                    bestDy = diff;
                    best = p.immutable();
                }
            }
        }
        return best != null && !best.equals(current) ? best : null;
    }
}

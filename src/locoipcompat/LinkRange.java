package locoipcompat;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

/** Alcance de Redstone Link usando a posição real no mundo de blocos que estão dentro de naves do Sable. */
public final class LinkRange {
    private static Method beAccessor;
    private static boolean reflectionFailed;

    private LinkRange() {
    }

    /** @return o resultado com a posição real, ou null quando não deu para calcular (usa o cálculo original) */
    public static Boolean withinRange(IRedstoneLinkable receiver, IRedstoneLinkable other) {
        try {
            Level level = levelOf(receiver);
            if (level == null) {
                return null;
            }
            Vector3d a = worldPos(level, receiver.getLocation());
            Vector3d b = worldPos(level, other.getLocation());
            int range = AllConfigs.server().logistics.linkRange.get();
            return a.distanceSquared(b) < (double) range * range;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Vector3d worldPos(Level level, BlockPos pos) {
        Vector3d v = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub != null) {
            sub.logicalPose().transformPosition(v);
        }
        return v;
    }

    /** O receptor do Loconautics é um record não público com o block entity dono em be(). */
    private static Level levelOf(IRedstoneLinkable receiver) throws Exception {
        if (reflectionFailed) {
            return null;
        }
        if (beAccessor == null || beAccessor.getDeclaringClass() != receiver.getClass()) {
            try {
                Method m = receiver.getClass().getDeclaredMethod("be");
                m.setAccessible(true);
                beAccessor = m;
            } catch (NoSuchMethodException e) {
                reflectionFailed = true;
                return null;
            }
        }
        Object be = beAccessor.invoke(receiver);
        return be instanceof BlockEntity blockEntity ? blockEntity.getLevel() : null;
    }
}

package locoipcompat;

import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vagões engatados (Knuckle do Loconautics) atravessando portais do IP Aero.
 * Cada engate guarda o id da nave parceira e a posição (coordenada interna) do engate do outro lado. O IP Aero
 * desloca as coordenadas internas de cada nave que atravessa (às vezes zero, às vezes centenas de blocos), então
 * a posição guardada fica velha, o Loconautics não acha mais o parceiro e desengata. Aqui as posições seguem o
 * deslocamento de cada nave, como o teletransporte do próprio Loconautics faz ("followPartnerPlots").
 */
public final class KnuckleHandoff {
    private static final Logger LOG = LoggerFactory.getLogger("LocoIpCompat");
    private static Set<UUID> teleportInFlight;
    private static boolean reflectionFailed;

    private KnuckleHandoff() {
    }

    /**
     * Marca a nave como "em teleporte" para o Loconautics enquanto o IP a move. Sem isso, ao ver a nave sair do
     * mundo de origem, o vigia de engates manda desengatar quem estava preso a ela.
     */
    public static void setMoving(UUID id, boolean moving) {
        Set<UUID> set = inFlight();
        if (set == null || id == null) {
            return;
        }
        if (moving) {
            set.add(id);
        } else {
            set.remove(id);
        }
    }

    /** Chamado uma vez por grupo, depois que o IP moveu todas as naves (id da nave -> deslocamento). */
    public static void followPartners(ServerLevel level, Map<UUID, BlockPos> shifts) {
        if (shifts == null || shifts.isEmpty()) {
            return;
        }
        try {
            for (UUID id : shifts.keySet()) {
                ServerSubLevel sub = SubLevelData.resolve(level, id);
                if (sub == null) {
                    continue;
                }
                for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
                    UUID partnerId = knuckle.getPartnerSubLevelId();
                    BlockPos mate = knuckle.getPartnerLocalPos();
                    if (partnerId == null || mate == null) {
                        continue;
                    }
                    BlockPos partnerShift = shifts.get(partnerId);
                    if (partnerShift != null && !partnerShift.equals(BlockPos.ZERO)) {
                        knuckle.movePartnerPos(mate.offset(partnerShift));
                    }
                    ServerSubLevel partner = SubLevelData.resolve(level, partnerId);
                    if (partner != null && knuckle.isConnected()) {
                        // vagões engatados não colidem entre si (as restrições de contato foram soltas na mudança)
                        SubLevelContacts.disableCoupled(sub, partner);
                        LOG.info("[LocoIP] engate do vagão {} continua preso ao {} do outro lado do portal",
                                shortId(id), shortId(partnerId));
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("[LocoIP] falha ao acompanhar os engates na travessia", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized Set<UUID> inFlight() {
        if (teleportInFlight != null || reflectionFailed) {
            return teleportInFlight;
        }
        try {
            Field f = SableTeleportation.class.getDeclaredField("IN_FLIGHT");
            f.setAccessible(true);
            teleportInFlight = (Set<UUID>) f.get(null);
        } catch (Throwable t) {
            reflectionFailed = true;
            LOG.warn("[LocoIP] a versão do Loconautics mudou: não deu para marcar naves em travessia ({})", t.toString());
        }
        return teleportInFlight;
    }

    private static String shortId(UUID id) {
        return id == null ? "?" : id.toString().substring(0, 8);
    }
}

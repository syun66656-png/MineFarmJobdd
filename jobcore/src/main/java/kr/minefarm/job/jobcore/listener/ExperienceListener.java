package kr.minefarm.job.jobcore.listener;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import kr.minefarm.job.jobcore.api.ExperienceBypass;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

/**
 * 바닐라 경험치 구슬·드롭 완전 차단.
 * {@link ExperienceBypass} 가 활성인 구간 또는 직업 EXP API 지급은 제외한다.
 */
public final class ExperienceListener implements Listener {

    private final boolean enabled;

    public ExperienceListener(boolean enabled) {
        this.enabled = enabled;
    }

    private boolean shouldBlock() {
        return enabled && !ExperienceBypass.isActive();
    }

    // ── 소스 이벤트: 드롭·지급량을 0으로 ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!shouldBlock()) {
            return;
        }
        event.setDroppedExp(0);
    }

    /** BlockBreakEvent 등 블록 경험치 드롭 (Paper 1.21+ BlockExpEvent). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        if (!shouldBlock()) {
            return;
        }
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!shouldBlock()) {
            return;
        }
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!shouldBlock()) {
            return;
        }
        event.setExperience(0);
    }

    /** /xp 등으로 오르는 바닐라 EXP 바 변화 무력화 (구슬 없이 직접 들어오는 경우). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (!shouldBlock()) {
            return;
        }
        if (event.getAmount() != 0) {
            event.setAmount(0);
        }
    }

    // ── 최종 방어: 구슬 엔티티 ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperienceOrbSpawn(EntitySpawnEvent event) {
        if (!shouldBlock()) {
            return;
        }
        if (!(event.getEntity() instanceof ExperienceOrb orb)) {
            return;
        }
        event.setCancelled(true);
        orb.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupExperience(PlayerPickupExperienceEvent event) {
        if (!shouldBlock()) {
            return;
        }
        event.setCancelled(true);
        ExperienceOrb orb = event.getExperienceOrb();
        if (orb.isValid()) {
            orb.remove();
        }
    }
}

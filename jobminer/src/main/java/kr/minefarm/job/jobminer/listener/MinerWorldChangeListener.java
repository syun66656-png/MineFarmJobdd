package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.passive.MinerPassiveEffectsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * 플레이어가 월드를 이동할 때 패시브 효과(가벼운 발걸음 Speed)를
 * 허용된 월드에서만 유지되도록 재평가한다.
 * <p>
 * {@link MinerPassiveEffectsService#apply}는 내부에서
 * {@code isPassiveAllowedInWorld()}를 체크하므로 호출만 다시 하면 된다.
 */
public final class MinerWorldChangeListener implements Listener {

    private final JobCoreAPI core;
    private final MinerPassiveEffectsService passiveEffects;

    public MinerWorldChangeListener(JobCoreAPI core, MinerPassiveEffectsService passiveEffects) {
        this.core = core;
        this.passiveEffects = passiveEffects;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        // 광부 아니거나 profile null이면 apply 내부에서 clear 처리됨
        passiveEffects.apply(player, profile);
    }
}

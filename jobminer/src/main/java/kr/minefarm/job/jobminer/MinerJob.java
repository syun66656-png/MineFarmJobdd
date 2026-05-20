package kr.minefarm.job.jobminer;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.kit.StarterKitService;
import kr.minefarm.job.jobminer.passive.MinerPassiveEffectsService;
import kr.minefarm.job.jobminer.skill.MinerSkills;
import org.bukkit.entity.Player;

/**
 * 광부 직업 정의.
 * <p>
 * 패시브/키트 적용은 JobCore에서 비동기 로드 후 {@code runTask}(메인 스레드) 안에서 호출된다.
 * 직업 교체 시 Core의 {@code JobService} 가 {@code onDeselect} → {@code onSelect} 순으로 호출해 패시브 중첩을 막는다.
 */
public final class MinerJob implements Job {

    private JobCoreAPI core;
    private StarterKitService starterKit;
    private MinerPassiveEffectsService passiveEffects;
    private MinerSkills minerSkills;

    /**
     * {@link JobMinerModule#onEnable} 에서 서비스를 연결한다.
     */
    public void bind(
            JobCoreAPI core,
            StarterKitService starterKit,
            MinerPassiveEffectsService passiveEffects,
            MinerSkills minerSkills
    ) {
        this.core = core;
        this.starterKit = starterKit;
        this.passiveEffects = passiveEffects;
        this.minerSkills = minerSkills;
    }

    public void unbind() {
        this.core = null;
        this.starterKit = null;
        this.passiveEffects = null;
        this.minerSkills = null;
    }

    @Override
    public JobId getId() {
        return JobId.MINER;
    }

    @Override
    public String getDisplayName() {
        return JobId.MINER.getDisplayName();
    }

    @Override
    public void onSelect(Player player) {
        player.sendMessage("§6[직업] §f광부 직업을 선택했습니다.");

        if (starterKit != null) {
            starterKit.equipStarterKit(player);
        }

        PlayerJobProfile profile = resolveProfile(player);
        if (passiveEffects != null && profile != null) {
            passiveEffects.clear(player);
            passiveEffects.apply(player, profile);
        }
    }

    @Override
    public void onDeselect(Player player) {
        if (passiveEffects != null) {
            passiveEffects.clear(player);
        }
        if (minerSkills != null) {
            minerSkills.clearForPlayer(player);
        }
    }

    /**
     * 접속 직후·스탯 투자 후 등, 캐시된 프로필이 반영된 뒤 메인 스레드에서 호출된다.
     * {@link MinerPassiveEffectsService#apply} 가 내부에서 우리 Speed를 먼저 제거한 뒤 새 스탯으로 다시 건다.
     */
    @Override
    public void refreshPassiveEffects(Player player, PlayerJobProfile profile) {
        if (passiveEffects == null) {
            return;
        }
        if (profile == null || profile.getJobId() != JobId.MINER) {
            passiveEffects.clear(player);
            return;
        }
        passiveEffects.apply(player, profile);
    }

    private PlayerJobProfile resolveProfile(Player player) {
        if (core == null) {
            return null;
        }
        return core.getPlayerProfiles().getCached(player.getUniqueId());
    }
}

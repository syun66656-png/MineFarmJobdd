package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 직업 전용 경험치 지급 및 레벨업 처리. 바닐라 구슬을 생성하지 않는다.
 * <p>
 * 경험치 추가 후 {@link ExperienceProgression#getRequiredForLevel} 기준으로
 * 레벨업 조건을 충족하면 레벨을 올리고 {@code statPointsPerLevel} 만큼 포인트를 지급한다.
 */
public final class JobExperienceService {

    private final JavaPlugin plugin;
    private final PlayerProfileService profileService;
    private final JobRegistry jobRegistry;
    private final ExperienceProgression experienceProgression;
    private final MessageConfig messageConfig;
    private int maxLevel;
    private int statPointsPerLevel;

    public JobExperienceService(
            JavaPlugin plugin,
            PlayerProfileService profileService,
            JobRegistry jobRegistry,
            ExperienceProgression experienceProgression,
            MessageConfig messageConfig,
            int maxLevel,
            int statPointsPerLevel
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.jobRegistry = jobRegistry;
        this.experienceProgression = experienceProgression;
        this.messageConfig = messageConfig;
        this.maxLevel = maxLevel;
        this.statPointsPerLevel = statPointsPerLevel;
    }

    /**
     * 설정 리로드 시 레벨 상한·포인트 비율을 갱신한다.
     */
    public void applyConfig(int maxLevel, int statPointsPerLevel) {
        this.maxLevel = maxLevel;
        this.statPointsPerLevel = statPointsPerLevel;
    }

    public CompletableFuture<PlayerJobProfile> grantExperience(Player player, long amount) {
        if (amount <= 0) {
            return profileService.loadOrCreate(player);
        }
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            // 레벨업 계산은 메인 스레드에서 수행 (다른 스레드 수정과 직렬화)
            CompletableFuture<PlayerJobProfile> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                applyExperienceAndLevelUp(player, profile, amount);
                done.complete(profile);
            });
            return done.thenCompose(p -> profileService.saveAsync(p).thenApply(ignored -> p));
        });
    }

    /**
     * 메인 스레드에서 경험치 추가 + 레벨업 처리.
     */
    private void applyExperienceAndLevelUp(Player player, PlayerJobProfile profile, long amount) {
        if (profile.getLevel() >= maxLevel) {
            // 최대 레벨 도달 시 경험치 누적 없이 종료
            return;
        }

        long pending = profile.getExperience() + amount;
        int level = profile.getLevel();
        int gainedLevels = 0;

        while (level < maxLevel) {
            long required = experienceProgression.getRequiredForLevel(level);
            if (pending < required) {
                break;
            }
            pending -= required;
            level++;
            gainedLevels++;
        }

        profile.setLevel(level);
        profile.setExperience(pending);

        if (gainedLevels > 0) {
            int gained = gainedLevels * statPointsPerLevel;
            profile.setStatPoints(profile.getStatPoints() + gained);

            if (player.isOnline()) {
                player.sendMessage(messageConfig.format("level-up", Map.of(
                        "level", String.valueOf(level),
                        "gained", String.valueOf(gainedLevels)
                )));
            }
        }

        // 패시브 갱신 (refreshPassiveEffects 호출)
        if (player.isOnline()) {
            jobRegistry.find(profile.getJobId()).ifPresent(job ->
                    job.refreshPassiveEffects(player, profile));
        }
    }
}

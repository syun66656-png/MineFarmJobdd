package kr.minefarm.job.jobcore.placeholder;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.ExperienceProgression;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import kr.minefarm.job.jobcore.service.RankingService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI 연동 — 식별자 {@code jobcore}.
 * <p>
 * %jobcore_job%, %jobcore_job_key%, %jobcore_level%, %jobcore_exp%,
 * %jobcore_stat_relic%, %jobcore_stat_skill%, %jobcore_stat_sell%, %jobcore_stat_auto_sell%,
 * %jobcore_auto_sell_enabled%, %jobcore_rank%
 * <p>
 * 캐시 미스 시 비동기 로드를 fire-and-forget 으로 트리거하고 즉시 기본값을 반환한다.
 * 로드 완료 후 다음 PAPI 요청부터는 캐시에서 응답된다.
 */
public final class JobCoreExpansion extends PlaceholderExpansion {

    private final String version;
    private final PlayerProfileService profileService;
    private final JobRegistry jobRegistry;
    private final ExperienceProgression experienceProgression;
    private final RankingService rankingService;

    public JobCoreExpansion(
            String version,
            PlayerProfileService profileService,
            JobRegistry jobRegistry,
            ExperienceProgression experienceProgression,
            RankingService rankingService
    ) {
        this.version = version;
        this.profileService = profileService;
        this.jobRegistry = jobRegistry;
        this.experienceProgression = experienceProgression;
        this.rankingService = rankingService;
    }

    @Override public @NotNull String getIdentifier() { return "jobcore"; }
    @Override public @NotNull String getAuthor() { return "MineFarm"; }
    @Override public @NotNull String getVersion() { return version; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerJobProfile profile = profileService.getCached(player.getUniqueId());
        if (profile == null) {
            // 캐시 미스: 비동기 warm-up 트리거 후 즉시 기본값 반환
            // 로드 완료 후 다음 요청부터 정상 응답됨
            if (player.isOnline() && player.getPlayer() != null) {
                profileService.loadOrCreate(player.getPlayer()); // fire-and-forget
            }
            return defaultForUnknown(params);
        }

        return resolve(params.toLowerCase(Locale.ROOT), profile);
    }

    private String resolve(String params, PlayerJobProfile profile) {
        return switch (params) {
            case "job" -> resolveJobDisplayName(profile);
            case "job_key" -> profile.getJobId().getKey();
            case "level" -> String.valueOf(profile.getLevel());
            case "exp" -> String.valueOf(profile.getExperience());
            case "exp_max" -> String.valueOf(experienceProgression.getRequiredForNextLevel(profile));
            case "exp_remaining" -> String.valueOf(experienceProgression.getExpUntilNextLevel(profile));
            case "exp_percent" -> formatExpPercent(profile);
            case "exp_bar" -> formatExpBar(profile);
            // BetterHud 등 외부 HUD 연동용
            case "exp_ratio" -> formatExpRatio(profile);       // 0.0 ~ 1.0
            case "max_level", "level_max" -> String.valueOf(experienceProgression.getMaxLevel());
            case "is_max_level" -> String.valueOf(profile.getLevel() >= experienceProgression.getMaxLevel());
            case "level_ratio" -> formatLevelRatio(profile);   // 0.0 ~ 1.0 (현재 레벨 / 최대 레벨)
            // 경험치 부스트 (스코어보드/탭리스트 표시용)
            case "boost_multiplier" -> formatBoostMultiplier(profile);
            case "boost_remaining" -> formatBoostRemaining(profile);
            case "is_boosting" -> String.valueOf(profile.isBoostActive());
            case "stat_points" -> String.valueOf(profile.getStatPoints());
            case "invested_stats" -> String.valueOf(profile.getInvestedStats());
            case "has_job" -> String.valueOf(profile.getJobId().hasJob());
            case "rank" -> rankingService.formatRank(
                    rankingService.getRank(profile.getUuid(), profile.getJobId()));
            case "auto_sell_enabled" -> String.valueOf(profile.isAutoSellEnabled());
            default -> resolveStatPlaceholder(params, profile);
        };
    }

    private String resolveStatPlaceholder(String params, PlayerJobProfile profile) {
        if (!params.startsWith("stat_")) return null;
        String statKey = params.substring("stat_".length());
        return StatType.fromKey(statKey)
                .map(type -> String.valueOf(profile.getStatLevel(type)))
                .orElse(null);
    }

    /** 경험치 진행률 (0.0 ~ 100.0, 소수점 1자리) */
    private String formatExpPercent(PlayerJobProfile profile) {
        long required = experienceProgression.getRequiredForNextLevel(profile);
        if (required <= 0) return "100.0";
        long remaining = experienceProgression.getExpUntilNextLevel(profile);
        double progress = (double) (required - remaining) / required * 100.0;
        if (progress < 0) progress = 0;
        if (progress > 100) progress = 100;
        return String.format(Locale.ROOT, "%.1f", progress);
    }

    /** 경험치 바 (10칸, 채워진 칸 █, 빈 칸 ░) */
    private String formatExpBar(PlayerJobProfile profile) {
        long required = experienceProgression.getRequiredForNextLevel(profile);
        if (required <= 0) return "██████████";
        long remaining = experienceProgression.getExpUntilNextLevel(profile);
        double progress = (double) (required - remaining) / required;
        int filled = (int) Math.round(progress * 10);
        if (filled < 0) filled = 0;
        if (filled > 10) filled = 10;
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < filled; i++) sb.append('█');
        for (int i = filled; i < 10; i++) sb.append('░');
        return sb.toString();
    }

    /** 경험치 진행률 0.0 ~ 1.0 (BetterHud progress bar 입력용, 소수점 4자리) */
    private String formatExpRatio(PlayerJobProfile profile) {
        long required = experienceProgression.getRequiredForNextLevel(profile);
        if (required <= 0) return "1.0";
        long remaining = experienceProgression.getExpUntilNextLevel(profile);
        double ratio = (double) (required - remaining) / required;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return String.format(Locale.ROOT, "%.4f", ratio);
    }

    /** 레벨 진행률 0.0 ~ 1.0 (현재 레벨 / 최대 레벨). 만렙이면 1.0 */
    private String formatLevelRatio(PlayerJobProfile profile) {
        int max = experienceProgression.getMaxLevel();
        if (max <= 0) return "1.0";
        double ratio = (double) profile.getLevel() / max;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return String.format(Locale.ROOT, "%.4f", ratio);
    }

    /** 부스트 배율 — 정수면 '2', 소수면 '1.5'. 비활성 시 '1' */
    private String formatBoostMultiplier(PlayerJobProfile profile) {
        if (!profile.isBoostActive()) return "1";
        double m = profile.getBoostMultiplier();
        if (m == Math.rint(m)) return String.valueOf((long) m);
        return String.format(Locale.ROOT, "%.1f", m);
    }

    /** 남은 시간 MM:SS, 비활성/만료면 '없음' */
    private String formatBoostRemaining(PlayerJobProfile profile) {
        long now = System.currentTimeMillis();
        long expiry = profile.getBoostExpiryTime();
        if (expiry <= now || profile.getBoostMultiplier() <= 1.0) return "없음";
        long remainingSec = (expiry - now + 999) / 1000;
        long mm = remainingSec / 60;
        long ss = remainingSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d", mm, ss);
    }

    private String resolveJobDisplayName(PlayerJobProfile profile) {
        JobId jobId = profile.getJobId();
        return jobRegistry.find(jobId)
                .map(job -> job.getDisplayName())
                .orElse(jobId.getDisplayName());
    }

    private String defaultForUnknown(String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "job" -> JobId.NONE.getDisplayName();
            case "job_key" -> JobId.NONE.getKey();
            case "level" -> "1";
            case "exp", "exp_remaining" -> "0";
            case "exp_percent" -> "0.0";
            case "exp_bar" -> "░░░░░░░░░░";
            case "exp_ratio" -> "0.0";
            case "max_level", "level_max" -> String.valueOf(experienceProgression.getMaxLevel());
            case "is_max_level" -> "false";
            case "level_ratio" -> "0.0";
            case "boost_multiplier" -> "1";
            case "boost_remaining" -> "없음";
            case "is_boosting" -> "false";
            case "exp_max" -> String.valueOf(experienceProgression.getRequiredForLevel(1));
            case "stat_points", "invested_stats" -> "0";
            case "has_job" -> "false";
            case "rank" -> "-";
            case "auto_sell_enabled" -> "false";
            default -> params.startsWith("stat_") ? "0" : null;
        };
    }
}

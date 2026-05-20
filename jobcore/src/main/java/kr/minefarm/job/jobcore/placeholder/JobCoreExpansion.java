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
 * %jobcore_auto_sell_enabled%
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

    @Override
    public @NotNull String getIdentifier() {
        return "jobcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MineFarm";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        PlayerJobProfile profile = profileService.getCached(player.getUniqueId());
        if (profile == null && player.isOnline() && player.getPlayer() != null) {
            profileService.loadOrCreate(player.getPlayer());
            profile = profileService.getCached(player.getUniqueId());
        }
        if (profile == null) {
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
        if (!params.startsWith("stat_")) {
            return null;
        }
        String statKey = params.substring("stat_".length());
        return StatType.fromKey(statKey)
                .map(type -> String.valueOf(profile.getStatLevel(type)))
                .orElse(null);
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
            case "exp_max" -> String.valueOf(experienceProgression.getRequiredForLevel(1));
            case "stat_points", "invested_stats" -> "0";
            case "has_job" -> "false";
            case "rank" -> "-";
            case "auto_sell_enabled" -> "false";
            default -> params.startsWith("stat_") ? "0" : null;
        };
    }
}

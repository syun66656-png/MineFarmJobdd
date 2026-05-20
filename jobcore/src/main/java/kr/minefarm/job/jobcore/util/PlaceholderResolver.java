package kr.minefarm.job.jobcore.util;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.integration.PluginHooks;
import kr.minefarm.job.jobcore.service.ExperienceProgression;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GUI 로어용 플레이스홀더 치환 + PAPI(선택) 파싱.
 * <p>
 * PlaceholderAPI 클래스와 메서드는 초기화 시 한 번만 룩업하여 캐싱한다.
 * 매 호출마다 Class.forName + getMethod 를 하던 비효율을 제거한다.
 */
public final class PlaceholderResolver {

    private final PluginHooks pluginHooks;
    private final ExperienceProgression experienceProgression;
    private final Logger logger;

    /** PAPI setPlaceholders 메서드 캐시 (null=미사용 또는 초기화 실패) */
    private Method cachedSetPlaceholders;
    private boolean papiMethodResolved = false;

    public PlaceholderResolver(
            PluginHooks pluginHooks,
            ExperienceProgression experienceProgression,
            Logger logger
    ) {
        this.pluginHooks = pluginHooks;
        this.experienceProgression = experienceProgression;
        this.logger = logger;
    }

    public String resolve(Player player, String text, PlayerJobProfile profile, int rank) {
        if (text == null || text.isEmpty()) return "";
        String result = applyInternal(text, buildPlaceholders(player, profile, rank));
        return applyPlaceholderApi(player, result);
    }

    public List<String> resolveLore(Player player, List<String> lore, PlayerJobProfile profile, int rank) {
        return lore.stream()
                .map(line -> resolve(player, line, profile, rank))
                .toList();
    }

    public static String buildExpBar(long exp, long expMax, int length) {
        if (expMax <= 0) return "§8" + "░".repeat(length);
        int filled = (int) Math.min(length, (exp * length) / expMax);
        return "§a" + "█".repeat(Math.max(0, filled))
                + "§8" + "░".repeat(Math.max(0, length - filled));
    }

    private Map<String, String> buildPlaceholders(Player player, PlayerJobProfile profile, int rank) {
        long expMax = experienceProgression.getRequiredForNextLevel(profile);
        long exp = profile.getExperience();
        Map<String, String> map = new HashMap<>();
        map.put("player", player.getName());
        map.put("job", profile.getJobId().getDisplayName());
        map.put("job_key", profile.getJobId().getKey());
        map.put("level", String.valueOf(profile.getLevel()));
        map.put("exp", String.valueOf(exp));
        map.put("exp_max", String.valueOf(expMax));
        map.put("exp_remaining", String.valueOf(experienceProgression.getExpUntilNextLevel(profile)));
        map.put("exp_percent", expMax > 0 ? String.valueOf((exp * 100) / expMax) : "0");
        map.put("exp_bar", buildExpBar(exp, expMax, 20));
        map.put("rank", rank > 0 ? String.valueOf(rank) : "-");
        map.put("stat_points", String.valueOf(profile.getStatPoints()));
        return map;
    }

    private static String applyInternal(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (!pluginHooks.isPlaceholderApiAvailable() || !text.contains("%")) return text;

        // 첫 호출 시에만 메서드를 룩업하여 캐싱 (이후 호출은 캐시 사용)
        if (!papiMethodResolved) {
            papiMethodResolved = true;
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                cachedSetPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
            } catch (ReflectiveOperationException exception) {
                logger.log(Level.FINE, "PlaceholderAPI method lookup failed", exception);
            }
        }

        if (cachedSetPlaceholders == null) return text;
        try {
            return (String) cachedSetPlaceholders.invoke(null, player, text);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.FINE, "PlaceholderAPI parse failed", exception);
            return text;
        }
    }
}

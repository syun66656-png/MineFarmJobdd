package kr.minefarm.job.jobcore.integration;

import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.ExperienceProgression;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import kr.minefarm.job.jobcore.service.RankingService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Soft dependency 플러그인 감지·연동.
 * PlaceholderAPI 클래스가 서버에 없어도 {@link #detect()} 는 예외 없이 동작한다.
 */
public final class PluginHooks {

    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";
    private static final String PLACEHOLDER_EXPANSION_BASE = "me.clip.placeholderapi.expansion.PlaceholderExpansion";
    private static final String JOB_CORE_EXPANSION = "kr.minefarm.job.jobcore.placeholder.JobCoreExpansion";

    private final JavaPlugin plugin;
    private boolean placeholderApiAvailable;

    private Object registeredExpansion;

    public PluginHooks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** onEnable 초기에 호출해 연동 가능 여부를 확정한다. */
    public void detect() {
        placeholderApiAvailable = isPluginPresentAndEnabled(PLACEHOLDER_API_PLUGIN)
                && isClassPresent(PLACEHOLDER_EXPANSION_BASE);

        if (placeholderApiAvailable) {
            plugin.getLogger().info("[JobCore] PlaceholderAPI detected.");
        } else {
            plugin.getLogger().info("[JobCore] PlaceholderAPI not available — placeholders disabled.");
        }
    }

    public boolean isPlaceholderApiAvailable() {
        return placeholderApiAvailable;
    }

    /**
     * PlaceholderAPI expansion 등록 (리플렉션).
     *
     * @return 등록 성공 시 true
     */
    public boolean registerJobCorePlaceholders(
            String version,
            PlayerProfileService profileService,
            JobRegistry jobRegistry,
            ExperienceProgression experienceProgression,
            RankingService rankingService
    ) {
        if (!placeholderApiAvailable) {
            return false;
        }

        try {
            Class<?> expansionClass = Class.forName(JOB_CORE_EXPANSION);
            Constructor<?> constructor = expansionClass.getConstructor(
                    String.class,
                    PlayerProfileService.class,
                    JobRegistry.class,
                    ExperienceProgression.class,
                    RankingService.class
            );
            registeredExpansion = constructor.newInstance(
                    version,
                    profileService,
                    jobRegistry,
                    experienceProgression,
                    rankingService
            );

            Method register = expansionClass.getMethod("register");
            boolean success = Boolean.TRUE.equals(register.invoke(registeredExpansion));
            if (success) {
                plugin.getLogger().info("[JobCore] PlaceholderAPI expansion registered (jobcore).");
            } else {
                plugin.getLogger().warning("[JobCore] PlaceholderAPI expansion register() returned false.");
                registeredExpansion = null;
            }
            return success;
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[JobCore] PlaceholderAPI classes missing at runtime — placeholders disabled.", exception);
            placeholderApiAvailable = false;
            return false;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[JobCore] Failed to register PlaceholderAPI expansion.", exception);
            registeredExpansion = null;
            return false;
        }
    }

    public void unregisterPlaceholders() {
        if (registeredExpansion == null) {
            return;
        }
        try {
            Method unregister = registeredExpansion.getClass().getMethod("unregister");
            unregister.invoke(registeredExpansion);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.FINE,
                    "[JobCore] PlaceholderAPI expansion unregister failed.", exception);
        } finally {
            registeredExpansion = null;
        }
    }

    private boolean isPluginPresentAndEnabled(String name) {
        Plugin target = Bukkit.getPluginManager().getPlugin(name);
        return target != null && target.isEnabled();
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}

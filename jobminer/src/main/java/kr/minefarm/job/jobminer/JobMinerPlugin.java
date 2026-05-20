package kr.minefarm.job.jobminer;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * 광부 직업 확장 플러그인. JobCore.jar가 서버에 먼저 로드되어야 한다.
 */
public final class JobMinerPlugin extends JavaPlugin {

    public static final String JOBCORE_PLUGIN_NAME = "JobCore";
    private static final String MODULE_CLASS = "kr.minefarm.job.jobminer.JobMinerModule";
    private static final String PROVIDER_CLASS = "kr.minefarm.job.jobcore.api.JobCoreProvider";

    private static final int REGISTER_RETRY_TICKS = 1;
    private static final int REGISTER_MAX_ATTEMPTS = 40;

    private boolean registered;
    private int registerAttempts;

    @Override
    public void onEnable() {
        registered = false;
        registerAttempts = 0;
        tryRegisterOrScheduleRetry();
    }

    private void tryRegisterOrScheduleRetry() {
        if (registered) {
            return;
        }

        Plugin corePlugin = findJobCorePlugin();
        if (corePlugin == null) {
            getLogger().severe("JobCore.jar 가 plugins/ 폴더에 없습니다.");
            getLogger().severe("jobcore/target/JobCore.jar 와 jobminer/target/JobMiner.jar 를 모두 넣었는지 확인하세요.");
            disableSelf();
            return;
        }

        if (!corePlugin.isEnabled()) {
            registerAttempts++;
            if (registerAttempts >= REGISTER_MAX_ATTEMPTS) {
                getLogger().severe("JobCore 가 " + REGISTER_MAX_ATTEMPTS + "틱 동안 활성화되지 않았습니다.");
                getLogger().severe("paper-plugin.yml 에서 JobCore 의 load: BEFORE 인지 확인하세요. (JobCore 가 JobMiner 보다 먼저 켜져야 합니다.)");
                getLogger().severe("또는 JobCore onEnable 중 오류로 비활성화된 경우 콘솔 위쪽 [JobCore] 로그를 확인하세요.");
                disableSelf();
                return;
            }
            getServer().getScheduler().runTaskLater(this, this::tryRegisterOrScheduleRetry, REGISTER_RETRY_TICKS);
            if (registerAttempts == 1) {
                getLogger().info("[JobMiner] JobCore 활성화 대기 중… (paper-plugin.yml: JobCore load: BEFORE 권장)");
            }
            return;
        }

        if (!registerWithJobCore(corePlugin)) {
            getLogger().severe("JobCore 에 모듈 등록에 실패했습니다.");
            getLogger().severe("JobCore.jar / JobMiner.jar 버전이 맞는지, Paper join-classpath 설정을 확인하세요.");
            disableSelf();
            return;
        }

        registered = true;
        getLogger().info("JobMiner registered with JobCore.");
    }

    private void disableSelf() {
        getServer().getPluginManager().disablePlugin(this);
    }

    private Plugin findJobCorePlugin() {
        Plugin byName = getServer().getPluginManager().getPlugin(JOBCORE_PLUGIN_NAME);
        if (byName != null) {
            return byName;
        }
        for (Plugin plugin : getServer().getPluginManager().getPlugins()) {
            if (JOBCORE_PLUGIN_NAME.equalsIgnoreCase(plugin.getName())) {
                return plugin;
            }
            if (plugin.getClass().getName().equals("kr.minefarm.job.jobcore.JobCorePlugin")) {
                return plugin;
            }
        }
        return null;
    }

    private boolean registerWithJobCore(Plugin corePlugin) {
        try {
            Class<?> providerType = Class.forName(PROVIDER_CLASS, false, corePlugin.getClass().getClassLoader());
            if (!providerType.isInstance(corePlugin)) {
                getLogger().severe("JobCore 플러그인이 JobCoreProvider 를 구현하지 않습니다. JobCore.jar 를 다시 빌드·배포하세요.");
                return false;
            }

            Method isModulesLoaded = providerType.getMethod("isModulesLoaded");
            if (Boolean.TRUE.equals(isModulesLoaded.invoke(corePlugin))) {
                getLogger().severe("JobCore 가 이미 직업 모듈 로드를 끝냈습니다. 서버를 완전히 재시작하세요.");
                return false;
            }

            Method registerHost = providerType.getMethod("registerHostJobModule", JavaPlugin.class, String.class);
            return Boolean.TRUE.equals(registerHost.invoke(corePlugin, this, MODULE_CLASS));
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.SEVERE, "[JobMiner] JobCore registration failed.", exception);
            return false;
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("JobMiner disabled.");
    }
}

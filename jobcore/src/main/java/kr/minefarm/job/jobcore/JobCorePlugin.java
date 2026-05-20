package kr.minefarm.job.jobcore;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.api.JobCoreProvider;
import kr.minefarm.job.jobcore.api.JobModule;
import kr.minefarm.job.jobcore.bootstrap.JobCoreBootstrap;
import kr.minefarm.job.jobcore.bootstrap.JobCoreState;
import kr.minefarm.job.jobcore.integration.HostJobModuleBridge;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * JobCore 플러그인 진입점. 직업 확장 모듈은 별도 JAR로 로드된다.
 */
public final class JobCorePlugin extends JavaPlugin implements JobCoreProvider {

    public static final String PLUGIN_NAME = "JobCore";

    private JobCoreBootstrap bootstrap;

    @Override
    public void onEnable() {
        bootstrap = new JobCoreBootstrap(this);
        if (!bootstrap.start()) {
            getLogger().severe("JobCore could not start. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(
                JobCoreProvider.class,
                this,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );

        getServer().getScheduler().runTaskLater(this, () -> {
            if (bootstrap.getState() == JobCoreState.INTEGRATIONS_READY) {
                bootstrap.loadJobModules();
            }
        }, 1L);

        getLogger().info("JobCore enabled — job modules will load after dependent plugins register.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (bootstrap != null && bootstrap.getState() != JobCoreState.SHUTDOWN) {
            bootstrap.shutdown();
        }
        getLogger().info("JobCore disabled.");
    }

  @Override
    public JobCoreAPI getApi() {
        return bootstrap != null ? bootstrap.getApi() : null;
    }

    @Override
    public boolean registerJobModule(JobModule module, JavaPlugin hostPlugin) {
        if (bootstrap == null || bootstrap.isModulesLoaded()) {
            return false;
        }
        bootstrap.registerModule(module, hostPlugin);
        return true;
    }

    @Override
    public boolean registerHostJobModule(JavaPlugin hostPlugin, String moduleClassName) {
        if (bootstrap == null || bootstrap.isModulesLoaded()) {
            return false;
        }
        try {
            HostJobModuleBridge bridge = HostJobModuleBridge.load(hostPlugin, moduleClassName, getApi());
            bootstrap.registerModule(bridge, hostPlugin);
            return true;
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.SEVERE,
                    "[JobCore] Failed to register host job module: " + moduleClassName, exception);
            return false;
        }
    }

    @Override
    public boolean isModulesLoaded() {
        return bootstrap != null && bootstrap.isModulesLoaded();
    }

    public JobCoreBootstrap getBootstrap() {
        return bootstrap;
    }

    public static JobCorePlugin getInstance() {
        return getPlugin(JobCorePlugin.class);
    }
}

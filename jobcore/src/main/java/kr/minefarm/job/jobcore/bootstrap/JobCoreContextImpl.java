package kr.minefarm.job.jobcore.bootstrap;

import kr.minefarm.job.jobcore.api.JobContext;
import kr.minefarm.job.jobcore.api.JobCoreAPI;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 직업 모듈에 전달되는 {@link JobContext} 구현.
 */
public final class JobCoreContextImpl implements JobContext {

    private final JavaPlugin plugin;
    private final JobCoreAPI coreApi;
    private final List<Listener> registeredListeners = new ArrayList<>();

    public JobCoreContextImpl(JavaPlugin plugin, JobCoreAPI coreApi) {
        this.plugin = plugin;
        this.coreApi = coreApi;
    }

    @Override
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public JobCoreAPI getCore() {
        return coreApi;
    }

    @Override
    public void registerListener(Listener listener) {
        registeredListeners.add(listener);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * 모듈 리로드·비활성화 시 등록한 리스너를 해제한다.
     */
    public void unregisterListeners() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    @Override
    public void registerCommands() {
        // JobCore 명령어는 JobCoreBootstrap.initializeIntegrations() 에서 등록
    }
}

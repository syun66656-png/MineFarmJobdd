package kr.minefarm.job.jobcore.integration.velocity;

import kr.minefarm.job.jobcore.api.VelocityProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * velocity-support 설정에 따라 Provider 생성·채널 등록을 관리한다.
 */
public final class VelocitySupport {

    private final JavaPlugin plugin;
    private VelocityProvider provider = NoOpVelocityProvider.INSTANCE;
    private VelocityProviderImpl implementation;

    public VelocitySupport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(boolean velocitySupportEnabled) {
        shutdown();
        if (!velocitySupportEnabled) {
            provider = NoOpVelocityProvider.INSTANCE;
            plugin.getLogger().info("[JobCore] Velocity support disabled (standalone Bukkit/Paper).");
            return;
        }
        implementation = new VelocityProviderImpl(plugin);
        implementation.start();
        provider = implementation;
    }

    public void shutdown() {
        if (implementation != null) {
            implementation.shutdown();
            implementation = null;
        }
        provider = NoOpVelocityProvider.INSTANCE;
    }

    public VelocityProvider getProvider() {
        return provider;
    }
}

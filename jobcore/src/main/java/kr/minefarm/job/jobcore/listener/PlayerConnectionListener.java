package kr.minefarm.job.jobcore.listener;

import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 접속 시 프로필 비동기 로드 후 메인 스레드에서 패시브 적용, 퇴장 시 캐시 해제·dirty 저장.
 */
public final class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerProfileService profileService;
    private final JobRegistry jobRegistry;

    public PlayerConnectionListener(
            JavaPlugin plugin,
            PlayerProfileService profileService,
            JobRegistry jobRegistry
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.jobRegistry = jobRegistry;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        profileService.loadOrCreate(player).whenComplete((profile, throwable) -> {
            if (throwable != null || profile == null) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                jobRegistry.find(profile.getJobId()).ifPresent(job ->
                        job.refreshPassiveEffects(player, profile));
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profileService.unload(event.getPlayer().getUniqueId());
    }
}

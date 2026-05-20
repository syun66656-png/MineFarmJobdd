package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.gui.AdminPlayerGui;
import kr.minefarm.job.jobcore.gui.GuiManager;
import kr.minefarm.job.jobcore.gui.JobInfoGui;
import kr.minefarm.job.jobcore.gui.JobSelectGui;
import kr.minefarm.job.jobcore.gui.StatGui;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * GUI 열기·클릭 후 비동기 서비스 호출 조율.
 */
public final class JobGuiService {

    private final JavaPlugin plugin;
    private final GuiConfig guiConfig;
    private final MessageConfig messages;
    private final GuiManager guiManager;
    private final PlayerProfileService profileService;
    private final JobService jobService;
    private final JobRegistry jobRegistry;
    private final RankingService rankingService;
    private final PlaceholderResolver placeholderResolver;
    private final StatService statService;

    public JobGuiService(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            MessageConfig messages,
            GuiManager guiManager,
            PlayerProfileService profileService,
            JobService jobService,
            JobRegistry jobRegistry,
            RankingService rankingService,
            PlaceholderResolver placeholderResolver,
            StatService statService
    ) {
        this.plugin = plugin;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.guiManager = guiManager;
        this.profileService = profileService;
        this.jobService = jobService;
        this.jobRegistry = jobRegistry;
        this.rankingService = rankingService;
        this.placeholderResolver = placeholderResolver;
        this.statService = statService;
    }

    /** /스탯 — 스탯 투자 GUI. */
    public void openStatGuiAsync(Player player) {
        profileService.loadOrCreate(player).thenAccept(profile ->
                runSync(() -> {
                    if (!profile.getJobId().hasJob()) {
                        player.sendMessage(messages.get("stat-no-job"));
                        return;
                    }
                    StatGui gui = new StatGui(
                            plugin,
                            guiConfig,
                            messages,
                            statService,
                            player,
                            statService.getMaxStatLevel()
                    );
                    openTracked(player, gui);
                }));
    }

    /** /직업 — 프로필 로드 후 분기. */
    public void openMainMenuAsync(Player player) {
        profileService.loadOrCreate(player).thenAccept(profile ->
                runSync(() -> openMainMenu(player, profile)));
    }

    private void openMainMenu(Player player, PlayerJobProfile profile) {
        if (!profile.getJobId().hasJob()) {
            openJobSelect(player);
            return;
        }
        int rank = rankingService.getRank(player.getUniqueId(), profile.getJobId());
        JobInfoGui gui = new JobInfoGui(
                plugin, guiConfig, placeholderResolver, rankingService, player, profile, rank
        );
        openTracked(player, gui);
    }

    public void openJobSelect(Player player) {
        JobSelectGui gui = new JobSelectGui(
                plugin, guiConfig, this, messages, jobRegistry
        );
        openTracked(player, gui);
    }

    /** 관리자가 대상 직업을 변경할 때 (골격 — 대상 온라인 시 동일 플로우). */
    public void openJobSelectForTarget(Player admin, UUID targetUuid, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (target.isOnline() && target.getPlayer() != null) {
            admin.sendMessage(messages.format("admin-open-gui", Map.of("target", targetName)));
            openJobSelect(target.getPlayer());
            return;
        }
        admin.sendMessage(messages.get("admin-player-not-found"));
    }

    public void openAdminGuiAsync(Player admin, OfflinePlayer target) {
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            runSync(() -> admin.sendMessage(messages.get("admin-player-not-found")));
            return;
        }
        profileService.loadOrCreate(target.getUniqueId()).thenAccept(profile ->
                runSync(() -> {
                    String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
                    int rank = rankingService.getRank(target.getUniqueId(), profile.getJobId());
                    AdminPlayerGui gui = new AdminPlayerGui(
                            plugin,
                            guiConfig,
                            this,
                            messages,
                            placeholderResolver,
                            admin,
                            target.getUniqueId(),
                            name,
                            profile,
                            rank
                    );
                    openTracked(admin, gui);
                }));
    }

    /**
     * 직업 선택 GUI 클릭 → 비동기 변경.
     *
     * @param onSuccess 메인 스레드 콜백 (null 가능)
     */
    public void selectJobAsync(Player player, JobId jobId, Runnable onSuccess) {
        jobService.changeJob(player, jobId).thenAccept(success ->
                runSync(() -> {
                    if (success) {
                        player.sendMessage(messages.format(
                                "job-select-success",
                                Map.of("job", jobId.getDisplayName())
                        ));
                        player.closeInventory();
                        if (onSuccess != null) {
                            onSuccess.run();
                        } else {
                            openMainMenuAsync(player);
                        }
                    } else {
                        player.sendMessage(messages.get("job-select-failed"));
                    }
                }));
    }

    private void openTracked(Player player, kr.minefarm.job.jobcore.gui.JobGui gui) {
        guiManager.track(player, gui);
        gui.open(player);
    }

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}

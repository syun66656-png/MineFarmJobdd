package kr.minefarm.job.jobcore.gui;

import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.service.JobGuiService;
import kr.minefarm.job.jobcore.util.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * 관리자용 플레이어 제어 GUI (골격).
 */
public final class AdminPlayerGui extends AbstractJobGui {

    private static final String ITEM_PROFILE = "profile-summary";
    private static final String ITEM_CHANGE_JOB = "change-job";
    private static final String ITEM_RESET = "reset-data";

    private final JobGuiService guiService;
    private final MessageConfig messages;
    private final PlaceholderResolver placeholderResolver;
    private final Player admin;
    private final UUID targetUuid;
    private final String targetName;
    private final PlayerJobProfile targetProfile;
    private final int targetRank;

    public AdminPlayerGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            JobGuiService guiService,
            MessageConfig messages,
            PlaceholderResolver placeholderResolver,
            Player admin,
            UUID targetUuid,
            String targetName,
            PlayerJobProfile targetProfile,
            int targetRank
    ) {
        super(plugin, guiConfig, "admin-player",
                guiConfig.getTitle("admin-player").replace("{target}", targetName));
        this.guiService = guiService;
        this.messages = messages;
        this.placeholderResolver = placeholderResolver;
        this.admin = admin;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetProfile = targetProfile;
        this.targetRank = targetRank;
        buildContent();
    }

    private void buildContent() {
        for (String itemKey : java.util.List.of(ITEM_PROFILE, ITEM_CHANGE_JOB, ITEM_RESET)) {
            guiConfig.getItem("admin-player", itemKey).ifPresent(template -> {
                String name = resolve(template.name());
                var lore = placeholderResolver.resolveLore(admin, template.lore(), targetProfile, targetRank)
                        .stream()
                        .map(line -> line.replace("{target}", targetName))
                        .toList();
                name = name.replace("{target}", targetName);
                setTemplateItem(template.slot(), template.material(), name, lore);
            });
        }
    }

    private String resolve(String text) {
        String withTarget = text.replace("{target}", targetName);
        return placeholderResolver.resolve(admin, withTarget, targetProfile, targetRank);
    }

    @Override
    public GuiType getType() {
        return GuiType.ADMIN_PLAYER;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(admin)) {
            return;
        }
        guiConfig.getItem("admin-player", ITEM_CHANGE_JOB).ifPresent(template -> {
            if (event.getSlot() == template.slot()) {
                player.closeInventory();
                guiService.openJobSelectForTarget(admin, targetUuid, targetName);
            }
        });
        guiConfig.getItem("admin-player", ITEM_RESET).ifPresent(template -> {
            if (event.getSlot() == template.slot()) {
                // Shift + 좌클릭일 때만 실행 (실수 방지)
                if (!event.isShiftClick() || !event.isLeftClick()) {
                    player.sendMessage(messages.get("admin-reset-confirm"));
                    return;
                }
                guiService.resetTargetAsync(admin, targetUuid, targetName);
            }
        });
    }
}

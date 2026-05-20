package kr.minefarm.job.jobcore.gui;

import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobcore.service.StatService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 스탯 투자·광부 자동판매 토글 GUI.
 */
public final class StatGui extends AbstractJobGui {

    private final MessageConfig messages;
    private final StatService statService;
    private final Player viewer;
    private final int maxStatLevel;

    private final Map<Integer, StatType> statSlots = new HashMap<>();
    private int autoSellToggleSlot = -1;

    public StatGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            MessageConfig messages,
            StatService statService,
            Player viewer,
            int maxStatLevel
    ) {
        super(plugin, guiConfig, "stat-gui");
        this.messages = messages;
        this.statService = statService;
        this.viewer = viewer;
        this.maxStatLevel = maxStatLevel;
        rebuild();
    }

    private PlayerJobProfile profile() {
        return statService.getProfileService().getCached(viewer.getUniqueId());
    }

    public void rebuild() {
        statSlots.clear();
        buildHeader();
        buildStatItems();
        buildAutoSellToggle();
    }

    private void buildHeader() {
        guiConfig.getStatGuiHeader().ifPresent(template -> {
            String name = applyPlaceholders(template.name());
            List<String> lore = template.lore().stream().map(this::applyPlaceholders).toList();
            setTemplateItem(template.slot(), template.material(), name, lore);
        });
    }

    private void buildStatItems() {
        for (Map.Entry<String, GuiConfig.GuiItemTemplate> entry : guiConfig.getStatGuiStatEntries().entrySet()) {
            StatType type = StatType.fromKey(entry.getKey()).orElse(null);
            if (type == null) {
                continue;
            }
            GuiConfig.GuiItemTemplate template = entry.getValue();
            statSlots.put(template.slot(), type);
            String name = applyPlaceholders(template.name());
            List<String> lore = template.lore().stream().map(this::applyPlaceholders).toList();
            setTemplateItem(template.slot(), template.material(), name, lore);
        }
    }

    private void buildAutoSellToggle() {
        PlayerJobProfile profile = profile();
        if (profile == null) {
            return;
        }
        guiConfig.getStatGuiAutoSellToggle().ifPresent(template -> {
            if (profile.getJobId() != JobId.MINER) {
                return;
            }
            autoSellToggleSlot = template.slot();
            Material material = profile.isAutoSellEnabled() ? Material.LIME_DYE : Material.GRAY_DYE;
            String name = applyPlaceholders(template.name());
            List<String> lore = template.lore().stream().map(this::applyPlaceholders).toList();
            setTemplateItem(template.slot(), material, name, lore);
        });
    }

    private String applyPlaceholders(String text) {
        if (text == null) {
            return "";
        }
        PlayerJobProfile profile = profile();
        if (profile == null) {
            return text;
        }
        String status = profile.isAutoSellEnabled() ? "ON" : "OFF";
        return text
                .replace("{stat_points}", String.valueOf(profile.getStatPoints()))
                .replace("{stat_max_level}", String.valueOf(maxStatLevel))
                .replace("{stat_relic}", String.valueOf(profile.getStatLevel(StatType.RELIC)))
                .replace("{stat_skill}", String.valueOf(profile.getStatLevel(StatType.SKILL)))
                .replace("{stat_sell}", String.valueOf(profile.getStatLevel(StatType.SELL)))
                .replace("{stat_auto_sell}", String.valueOf(profile.getStatLevel(StatType.AUTO_SELL)))
                .replace("{auto_sell_status}", status);
    }

    @Override
    public GuiType getType() {
        return GuiType.STAT;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(viewer)) {
            return;
        }
        int slot = event.getSlot();

        if (slot == autoSellToggleSlot && autoSellToggleSlot >= 0) {
            statService.toggleAutoSellAsync(player).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> onToggleResult(player, result)));
            return;
        }

        StatType type = statSlots.get(slot);
        if (type != null) {
            statService.investAsync(player, type).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> onInvestResult(player, type, result)));
        }
    }

    private void onInvestResult(Player player, StatType type, StatService.InvestResult result) {
        PlayerJobProfile cached = statService.getProfileService().getCached(player.getUniqueId());
        if (cached == null) {
            return;
        }
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(messages.format("stat-invest-success", Map.of(
                        "stat", type.getDisplayName(),
                        "points", String.valueOf(cached.getStatPoints())
                )));
                rebuild();
            }
            case NO_POINTS -> player.sendMessage(messages.get("stat-no-points"));
            case MAX_LEVEL -> player.sendMessage(messages.format("stat-max-level", Map.of(
                    "max", String.valueOf(maxStatLevel)
            )));
            case NO_JOB -> player.sendMessage(messages.get("stat-no-job"));
        }
    }

    private void onToggleResult(Player player, StatService.ToggleResult result) {
        PlayerJobProfile cached = statService.getProfileService().getCached(player.getUniqueId());
        if (cached == null) {
            return;
        }
        switch (result) {
            case SUCCESS -> {
                String status = cached.isAutoSellEnabled() ? "ON" : "OFF";
                player.sendMessage(messages.format("stat-toggle-success", Map.of("status", status)));
                rebuild();
            }
            case NOT_MINER -> player.sendMessage(messages.get("stat-toggle-not-miner"));
            case NO_JOB -> player.sendMessage(messages.get("stat-no-job"));
        }
    }
}

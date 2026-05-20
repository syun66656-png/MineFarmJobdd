package kr.minefarm.job.jobcore.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * gui.yml — GUI 제목·슬롯·아이템 템플릿.
 */
public final class GuiConfig {

    private FileConfiguration config;

    public GuiConfig(JavaPlugin plugin) {
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String getTitle(String guiKey) {
        return colorize(config.getString(guiKey + ".title", "&8GUI"));
    }

    public int getSize(String guiKey) {
        int size = config.getInt(guiKey + ".size", 27);
        return normalizeSize(size);
    }

    public Optional<GuiItemTemplate> getFiller(String guiKey) {
        return readItem(guiKey + ".filler", -1);
    }

    public Optional<GuiItemTemplate> getItem(String guiKey, String itemKey) {
        return readItem(guiKey + ".items." + itemKey, config.getInt(guiKey + ".items." + itemKey + ".slot", -1));
    }

    public Optional<GuiItemTemplate> getStatGuiHeader() {
        return readItem("stat-gui.header", config.getInt("stat-gui.header.slot", 4));
    }

    public Map<String, GuiItemTemplate> getStatGuiStatEntries() {
        ConfigurationSection section = config.getConfigurationSection("stat-gui.stats");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, GuiItemTemplate> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            readItem("stat-gui.stats." + key, section.getInt(key + ".slot", -1))
                    .ifPresent(template -> map.put(key, template));
        }
        return map;
    }

    public Optional<GuiItemTemplate> getStatGuiAutoSellToggle() {
        return readItem("stat-gui.auto-sell-toggle", config.getInt("stat-gui.auto-sell-toggle.slot", 31));
    }

    public Map<String, GuiItemTemplate> getJobSelectEntries() {
        ConfigurationSection section = config.getConfigurationSection("job-select.jobs");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, GuiItemTemplate> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            readItem("job-select.jobs." + key, section.getInt(key + ".slot", -1))
                    .ifPresent(template -> map.put(key, template));
        }
        return map;
    }

    private Optional<GuiItemTemplate> readItem(String path, int slotOverride) {
        if (!config.contains(path + ".material")) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(config.getString(path + ".material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        int slot = slotOverride >= 0 ? slotOverride : config.getInt(path + ".slot", 0);
        String name = colorize(config.getString(path + ".name", " "));
        List<String> lore = config.getStringList(path + ".lore").stream()
                .map(GuiConfig::colorize)
                .toList();
        return Optional.of(new GuiItemTemplate(slot, material, name, lore));
    }

    private static int normalizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size % 9 != 0) {
            return ((size / 9) + 1) * 9;
        }
        return Math.min(size, 54);
    }

    private static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§');
    }

    public record GuiItemTemplate(int slot, Material material, String name, List<String> lore) {
    }
}

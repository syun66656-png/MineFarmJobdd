package kr.minefarm.job.jobcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

/**
 * messages.yml — 명령어·시스템 메시지.
 */
public final class MessageConfig {

    private FileConfiguration config;

    public MessageConfig(JavaPlugin plugin) {
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        return colorize(config.getString("messages." + path, "&cMessage missing: " + path));
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§');
    }
}

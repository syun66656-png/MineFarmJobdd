package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobminer.JobMinerModule;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * jobminer/regen_blocks.yml 영속화.
 */
public final class RegenBlockStorage {

    private final JavaPlugin plugin;
    private final File file;

    public RegenBlockStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), JobMinerModule.MODULE_ID + "/regen_blocks.yml");
    }

    public List<RegenBlockEntry> load() {
        if (!file.exists()) {
            return List.of();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> rawList = config.getMapList("blocks");
        if (rawList.isEmpty()) {
            return List.of();
        }

        List<RegenBlockEntry> entries = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            RegenBlockEntry entry = readEntry(raw);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public void save(Collection<RegenBlockEntry> entries) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RegenBlockEntry entry : entries) {
            serialized.add(writeEntry(entry));
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("blocks", serialized);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[JobMiner] Failed to save regen_blocks.yml", exception);
        }
    }

    private static RegenBlockEntry readEntry(Map<?, ?> map) {
        Object worldId = map.get("world");
        if (worldId == null) {
            return null;
        }
        String worldName = getString(map, "world-name", "world");
        int x = toInt(map.get("x"));
        int y = toInt(map.get("y"));
        int z = toInt(map.get("z"));
        Material material = RegenBlockEntry.parseMaterial(getString(map, "material", "STONE"));
        String blockData = map.get("block-data") != null ? String.valueOf(map.get("block-data")) : null;
        if (blockData == null || blockData.isBlank()) {
            blockData = material.createBlockData().getAsString();
        }
        return new RegenBlockEntry(worldId.toString(), worldName, x, y, z, material, blockData);
    }

    private static Map<String, Object> writeEntry(RegenBlockEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", entry.getWorldId());
        map.put("world-name", entry.getWorldName());
        map.put("x", entry.getX());
        map.put("y", entry.getY());
        map.put("z", entry.getZ());
        map.put("material", entry.getMaterial().name());
        map.put("block-data", entry.getBlockDataString());
        return map;
    }

    private static String getString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}

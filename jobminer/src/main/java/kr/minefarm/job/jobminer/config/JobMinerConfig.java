package kr.minefarm.job.jobminer.config;

import kr.minefarm.job.jobminer.JobMinerModule;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * plugins/MineFarmJob/jobminer/config.yml
 */
public final class JobMinerConfig {

    private final FileConfiguration config;
    private final Map<Material, Integer> guaranteedDrops;
    private final List<SpecialDrop> specialDrops;
    private final Map<Material, Double> shopPrices;
    private final boolean dynamiteEnabled;
    private final Material dynamiteMaterial;
    private final String dynamiteNameKeyword;
    private final int dynamiteFuseTicks;
    private final boolean dynamiteConsumeItem;
    private final int dynamiteCooldownTicks;
    private final boolean overclockEnabled;
    private final Material overclockMaterial;
    private final String overclockNameKeyword;
    private final int overclockCooldownTicks;
    private final int overclockDurationTicks;
    private final int overclockMinJobLevel;
    private final int overclockMaxJobLevel;
    private final int overclockHasteAmplifier;
    private final String overclockCooldownMessage;
    private final String overclockLevelDeniedMessage;

    public JobMinerConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), JobMinerModule.MODULE_ID + "/config.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        this.guaranteedDrops = loadGuaranteedDrops();
        this.specialDrops = loadSpecialDrops();
        this.shopPrices = loadShopPrices();
        this.dynamiteEnabled = config.getBoolean("dynamite.enabled", false);
        this.dynamiteMaterial = parseMaterial(config.getString("dynamite.material", "FIRE_CHARGE"));
        this.dynamiteNameKeyword = config.getString("dynamite.name-keyword", "");
        this.dynamiteFuseTicks = Math.max(1, config.getInt("dynamite.fuse-ticks", 50));
        this.dynamiteConsumeItem = config.getBoolean("dynamite.consume-item", true);
        this.dynamiteCooldownTicks = Math.max(0, config.getInt("dynamite.cooldown-ticks", 100));
        this.overclockEnabled = config.getBoolean("overclock.enabled", false);
        Material ocMat = parseMaterial(config.getString("overclock.material", "CLOCK"));
        this.overclockMaterial = ocMat != null ? ocMat : Material.CLOCK;
        this.overclockNameKeyword = config.getString("overclock.name-keyword", "");
        this.overclockCooldownTicks = Math.max(0, config.getInt("overclock.cooldown-ticks", 600));
        this.overclockDurationTicks = Math.max(1, config.getInt("overclock.duration-ticks", 200));
        this.overclockMinJobLevel = Math.max(1, config.getInt("overclock.min-job-level", 1));
        int maxJob = config.getInt("overclock.max-job-level", 999);
        this.overclockMaxJobLevel = Math.max(this.overclockMinJobLevel, maxJob);
        this.overclockHasteAmplifier = Math.max(0, config.getInt("overclock.haste-amplifier", 1));
        this.overclockCooldownMessage = config.getString(
                "overclock.cooldown-message",
                "&c[광부] &f쿨타임 중입니다."
        );
        this.overclockLevelDeniedMessage = config.getString(
                "overclock.level-denied-message",
                "&c[광부] &f직업 레벨이 부족하거나 초과하여 오버클럭을 사용할 수 없습니다."
        );
    }

    public List<String> getAllowedPickaxeKeywords() {
        return config.getStringList("allowed_pickaxe_keywords");
    }

    public int getRegenDelaySeconds() {
        return config.getInt("regen.delay-seconds", 30);
    }

    public Material getRegenWandMaterial() {
        String name = config.getString("regen-wand.material", "GOLDEN_AXE");
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Material.GOLDEN_AXE;
        }
    }

    public String getRegenWandDisplayName() {
        return config.getString("regen-wand.display-name", "&6광산 관리 완드");
    }

    public List<String> getRegenWandLore() {
        return config.getStringList("regen-wand.lore");
    }

    public boolean isAutoSellNotifyPlayer() {
        return config.getBoolean("autosell.notify-player", true);
    }

    public double getAutoSellBaseChance() {
        return config.getDouble("autosell.base-chance", 0.05D);
    }

    public double getAutoSellChancePerLevel() {
        return config.getDouble("autosell.chance-per-level", 0.02D);
    }

    public double getAutoSellMaxChance() {
        return config.getDouble("autosell.max-chance", 0.80D);
    }

    public String getAutoSellMessage() {
        return config.getString("autosell.message", "&a[자동판매] &f{summary} &8→ &6+{total_price}골드");
    }

    public double getSellBonusMultiplier() {
        return config.getDouble("sell.bonus-multiplier", 0.05D);
    }

    public double getShopBasePrice(Material material) {
        return shopPrices.getOrDefault(material, 0D);
    }

    /** 가격이 설정된 머티리얼 목록 (상점 GUI 카탈로그용). */
    public java.util.List<Material> getShopPricedMaterials() {
        return shopPrices.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /** 상점 GUI 제목 템플릿 */
    public String getShopGuiTitle() {
        return config.getString("shop-gui.title", "&6&l광부 상점 &8— &e{page}&8/&f{total_pages}");
    }

    public boolean isShopNotifyOnSell() {
        return config.getBoolean("shop-gui.notify-on-sell", true);
    }

    public String getShopSellMessage() {
        return config.getString("shop-gui.sell-message",
                "&a[상점] &f{item} &7{amount}개 판매 → &6+{price}골드");
    }

    public String getShopSellAllMessage() {
        return config.getString("shop-gui.sell-all-message",
                "&a[상점] &f총 {count}종 판매 → &6+{total}골드");
    }

    public String getShopNoItemsMessage() {
        return config.getString("shop-gui.no-items-message",
                "&c인벤토리에 판매 가능한 광석이 없습니다.");
    }

    public String getShopVaultErrorMessage() {
        return config.getString("shop-gui.vault-error-message",
                "&c입금 오류가 발생했습니다. 관리자에게 문의하세요.");
    }

    public Map<Material, Integer> getGuaranteedDrops() {
        return guaranteedDrops;
    }

    public List<SpecialDrop> getSpecialDrops() {
        return specialDrops;
    }

    public record SpecialDrop(Material material, double chance, int amount) {
    }

    private Map<Material, Integer> loadGuaranteedDrops() {
        ConfigurationSection section = config.getConfigurationSection("guaranteed-drops");
        if (section == null) {
            return Map.of();
        }
        Map<Material, Integer> map = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material != null) {
                map.put(material, Math.max(1, section.getInt(key, 1)));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private List<SpecialDrop> loadSpecialDrops() {
        ConfigurationSection section = config.getConfigurationSection("special-drops");
        if (section == null) {
            return List.of();
        }
        List<SpecialDrop> list = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) {
                continue;
            }
            ConfigurationSection drop = section.getConfigurationSection(key);
            if (drop == null) {
                continue;
            }
            list.add(new SpecialDrop(
                    material,
                    drop.getDouble("chance", 0D),
                    Math.max(1, drop.getInt("amount", 1))
            ));
        }
        return List.copyOf(list);
    }

    private Map<Material, Double> loadShopPrices() {
        ConfigurationSection section = config.getConfigurationSection("shop-prices");
        if (section == null) {
            return Map.of();
        }
        Map<Material, Double> map = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material != null) {
                map.put(material, section.getDouble(key, 0D));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public boolean isDynamiteEnabled() {
        return dynamiteEnabled;
    }

    public Material getDynamiteMaterial() {
        return dynamiteMaterial != null ? dynamiteMaterial : Material.FIRE_CHARGE;
    }

    public String getDynamiteNameKeyword() {
        return dynamiteNameKeyword != null ? dynamiteNameKeyword : "";
    }

    public int getDynamiteFuseTicks() {
        return dynamiteFuseTicks;
    }

    public boolean isDynamiteConsumeItem() {
        return dynamiteConsumeItem;
    }

    public int getDynamiteCooldownTicks() {
        return dynamiteCooldownTicks;
    }

    public boolean isOverclockEnabled() {
        return overclockEnabled;
    }

    public Material getOverclockMaterial() {
        return overclockMaterial;
    }

    public String getOverclockNameKeyword() {
        return overclockNameKeyword != null ? overclockNameKeyword : "";
    }

    public int getOverclockCooldownTicks() {
        return overclockCooldownTicks;
    }

    public int getOverclockDurationTicks() {
        return overclockDurationTicks;
    }

    public int getOverclockMinJobLevel() {
        return overclockMinJobLevel;
    }

    public int getOverclockMaxJobLevel() {
        return overclockMaxJobLevel;
    }

    public int getOverclockHasteAmplifier() {
        return overclockHasteAmplifier;
    }

    public String getOverclockCooldownMessage() {
        return overclockCooldownMessage != null ? overclockCooldownMessage : "&c[광부] &f쿨타임 중입니다.";
    }

    public String getOverclockLevelDeniedMessage() {
        return overclockLevelDeniedMessage != null ? overclockLevelDeniedMessage : "";
    }

    public ConfigurationSection getStarterKitSection() {
        return config.getConfigurationSection("starter-kit");
    }

    public ConfigurationSection getMinerPassivesSection() {
        return config.getConfigurationSection("miner-passives");
    }

    private static Material parseMaterial(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

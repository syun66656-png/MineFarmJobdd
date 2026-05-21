package kr.minefarm.job.jobminer.config;

import kr.minefarm.job.jobminer.JobMinerModule;
import kr.minefarm.job.jobminer.mining.MineExpTable;
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
import java.util.stream.Collectors;

/**
 * plugins/MineFarmJob/jobminer/config.yml
 */
public final class JobMinerConfig {

    private final FileConfiguration config;
    private final Map<Material, Integer> guaranteedDrops;
    private final List<SpecialDrop> specialDrops;
    private final Map<Material, List<OreDrop>> oreDrops;
    private final List<CommonDrop> commonDrops;
    private final java.util.Set<String> passiveAllowedRegions;
    private final Map<Material, Double> shopPrices;
    private final MineExpTable mineExpTable;
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
        this.oreDrops = loadOreDrops();
        this.commonDrops = loadCommonDrops();
        this.passiveAllowedRegions = loadPassiveAllowedRegions();
        this.shopPrices = loadShopPrices();
        this.mineExpTable = loadMineExpTable();
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

    public Material getRegenReplacementMaterial() {
        String name = config.getString("regen.replacement-material", "COBBLESTONE");
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Material.COBBLESTONE;
        }
    }

    public boolean isRegenReplacementEnabled() {
        return config.getBoolean("regen.replacement-enabled", true);
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
                .collect(Collectors.toList());
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

    /** 광물별 지정 드롭 (블록 Material에 따른 드롭 목록) */
    public java.util.List<OreDrop> getOreDropsFor(Material blockMaterial) {
        if (blockMaterial == null) return java.util.List.of();
        return oreDrops.getOrDefault(blockMaterial, java.util.List.of());
    }

    public java.util.Map<Material, java.util.List<OreDrop>> getAllOreDrops() {
        return oreDrops;
    }

    /** 공통 확률 드롭 (강화석/초월석/유물 등) */
    public java.util.List<CommonDrop> getCommonDrops() {
        return commonDrops;
    }

    /** 광부 패시브가 적용될 WorldGuard 리전 ID 목록 (비어 있으면 제한 없음 → 모든 곳에서 적용) */
    public java.util.Set<String> getPassiveAllowedRegions() {
        return passiveAllowedRegions;
    }

    public List<SpecialDrop> getSpecialDrops() {
        return specialDrops;
    }

    public record SpecialDrop(Material material, double chance, int amount) {
    }

    /** 광물 종류별 지정 드롭 (채굴된 블록 Material에 따라 다른 드롭) */
    public record OreDrop(Material material, int amount, String displayName, java.util.List<String> lore) {
    }

    /** 모든 광물에서 공통으로 등장하는 확률 드롭 (강화석/초월석/유물 등) */
    public record CommonDrop(Material material, int amount, double chance, String displayName, java.util.List<String> lore) {
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

    private Map<Material, List<OreDrop>> loadOreDrops() {
        ConfigurationSection section = config.getConfigurationSection("ore-drops");
        if (section == null) return Map.of();
        Map<Material, List<OreDrop>> map = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material ore = parseMaterial(key);
            if (ore == null) continue;
            List<?> rawList = section.getList(key);
            if (rawList == null || rawList.isEmpty()) continue;
            List<OreDrop> drops = new ArrayList<>();
            for (Object obj : rawList) {
                if (!(obj instanceof Map<?, ?> entry)) continue;
                Material mat = parseMaterial(String.valueOf(entry.get("material")));
                if (mat == null) continue;
                int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
                String name = entry.get("name") instanceof String s ? s : null;
                @SuppressWarnings("unchecked")
                List<String> lore = entry.get("lore") instanceof List<?> l
                        ? l.stream().map(String::valueOf).collect(Collectors.toList())
                        : List.of();
                drops.add(new OreDrop(mat, amount, name, lore));
            }
            if (!drops.isEmpty()) map.put(ore, List.copyOf(drops));
        }
        return Collections.unmodifiableMap(map);
    }

    private List<CommonDrop> loadCommonDrops() {
        List<?> rawList = config.getList("common-drops");
        if (rawList == null || rawList.isEmpty()) return List.of();
        List<CommonDrop> list = new ArrayList<>();
        for (Object obj : rawList) {
            if (!(obj instanceof Map<?, ?> entry)) continue;
            Material mat = parseMaterial(String.valueOf(entry.get("material")));
            if (mat == null) continue;
            int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            double chance = entry.get("chance") instanceof Number c ? c.doubleValue() : 0.0;
            if (chance <= 0.0) continue;
            String name = entry.get("name") instanceof String s ? s : null;
            @SuppressWarnings("unchecked")
            List<String> lore = entry.get("lore") instanceof List<?> l
                    ? l.stream().map(String::valueOf).collect(Collectors.toList())
                    : List.of();
            list.add(new CommonDrop(mat, amount, chance, name, lore));
        }
        return List.copyOf(list);
    }

    private java.util.Set<String> loadPassiveAllowedRegions() {
        ConfigurationSection root = config.getConfigurationSection("miner-passives");
        if (root == null) return java.util.Set.of();
        List<String> regions = root.getStringList("allowed-regions");
        if (regions == null || regions.isEmpty()) return java.util.Set.of();
        return java.util.Set.copyOf(regions);
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

    /**
     * mine-exp 섹션을 읽어 광물별 경험치 테이블을 구성한다.
     * 값 형식: "20~35" (범위) 또는 "500" (고정값).
     */
    private MineExpTable loadMineExpTable() {
        ConfigurationSection section = config.getConfigurationSection("mine-exp");
        if (section == null) {
            return new MineExpTable(new EnumMap<>(Material.class));
        }
        Map<Material, MineExpTable.ExpRange> map = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) continue;
            String raw = section.getString(key, "0");
            if (raw == null) raw = String.valueOf(section.getLong(key, 0L));
            MineExpTable.ExpRange range = MineExpTable.ExpRange.parse(raw);
            if (range.hasExp()) {
                map.put(material, range);
            }
        }
        return new MineExpTable(Collections.unmodifiableMap(map));
    }

    /** 광물별 채굴 경험치 테이블 */
    public MineExpTable getMineExpTable() {
        return mineExpTable;
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

    /** RelicStatService 등 내부 서비스가 임의 섹션에 접근할 때 사용 */
    public org.bukkit.configuration.file.FileConfiguration getRawConfig() {
        return config;
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

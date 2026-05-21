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
    private final Map<Material, List<OreDrop>> oreDrops;
    private final List<CommonDrop> commonDrops;
    private final java.util.Set<String> passiveAllowedRegions;
    private final java.util.Set<String> mineAllowedRegions;
    private final java.util.List<ShopGuiItem> shopGuiItems;
    private final MineExpTable mineExpTable;
    private final boolean dynamiteEnabled;
    private final int dynamiteFuseTicks;
    private final int dynamiteCooldownTicks;
    private final boolean overclockEnabled;
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
        this.oreDrops = loadOreDrops();
        this.commonDrops = loadCommonDrops();
        this.passiveAllowedRegions = loadPassiveAllowedRegions();
        this.mineAllowedRegions = loadMineAllowedRegions();
        this.shopGuiItems = loadShopGuiItems();
        this.mineExpTable = loadMineExpTable();
        this.dynamiteEnabled = config.getBoolean("dynamite.enabled", false);
        this.dynamiteFuseTicks = Math.max(1, config.getInt("dynamite.fuse-ticks", 50));
        this.dynamiteCooldownTicks = Math.max(0, config.getInt("dynamite.cooldown-ticks", 100));
        this.overclockEnabled = config.getBoolean("overclock.enabled", false);
        Material ocMat = parseMaterial(config.getString("overclock.material", "CLOCK"));
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

    /** 상점 GUI 제목 */
    public String getShopGuiTitle() {
        return config.getString("shop-gui.title", "&6&l광부 상점");
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

    /** 광부 상점 GUI 카탈로그 슬롯 정의 목록 */
    public java.util.List<ShopGuiItem> getShopGuiItems() {
        return shopGuiItems;
    }

    /**
     * 인벤토리 ItemStack과 매칭되는 ore-drop/common-drop의 단가 반환.
     * 매칭 기준: Material + customModelData + displayName(평문).
     * 매칭 안 되면 -1 반환. 0은 "매칭되지만 미판매".
     */
    public double findShopPriceForItem(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return -1;
        Material mat = stack.getType();
        Integer cmd = extractCustomModelData(stack);
        String plain = extractPlainName(stack);

        for (java.util.List<OreDrop> list : oreDrops.values()) {
            for (OreDrop d : list) {
                if (d.material() != mat) continue;
                if (!matches(d.customModelData(), cmd)) continue;
                if (!matchesName(d.displayName(), plain)) continue;
                return d.shopPrice();
            }
        }
        for (CommonDrop d : commonDrops) {
            if (d.material() != mat) continue;
            if (!matches(d.customModelData(), cmd)) continue;
            if (!matchesName(d.displayName(), plain)) continue;
            return d.shopPrice();
        }
        return -1;
    }

    private static boolean matches(Integer a, Integer b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.intValue() == b.intValue();
    }

    private static boolean matchesName(String configDisplay, String itemPlain) {
        boolean noConfig = configDisplay == null || configDisplay.isBlank();
        boolean noItem = itemPlain == null || itemPlain.isBlank();
        if (noConfig && noItem) return true;
        if (noConfig || noItem) return false;
        // config는 &색코드 포함 가능 → plain 비교
        String configPlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(configDisplay));
        return configPlain.equals(itemPlain);
    }

    private static Integer extractCustomModelData(org.bukkit.inventory.ItemStack stack) {
        if (!stack.hasItemMeta()) return null;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return null;
        return meta.getCustomModelData();
    }

    private static String extractPlainName(org.bukkit.inventory.ItemStack stack) {
        if (!stack.hasItemMeta()) return null;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
    }

    /** 광부 패시브가 적용될 WorldGuard 리전 ID 목록 (비어 있으면 제한 없음 → 모든 곳에서 적용) */
    public java.util.Set<String> getPassiveAllowedRegions() {
        // worldguard.allowed-regions 가 우선. 비어 있으면 miner-passives.allowed-regions(레거시).
        if (!mineAllowedRegions.isEmpty()) return mineAllowedRegions;
        return passiveAllowedRegions;
    }

    /**
     * 광산 시스템(리젠 블록 등록·채굴·드롭·스킬)이 적용될 WorldGuard 리전 ID 목록.
     * 비어 있으면 제한 없음(모든 곳에서 광산 시스템 동작).
     */
    public java.util.Set<String> getMineAllowedRegions() {
        return mineAllowedRegions;
    }

    /**
     * 광물 종류별 지정 드롭.
     * customModelData: null이면 미적용. shopPrice: 0이면 광부상점에서 미판매.
     */
    public record OreDrop(
            Material material,
            int amount,
            String displayName,
            java.util.List<String> lore,
            Integer customModelData,
            double shopPrice
    ) {
    }

    /** 모든 광물에서 공통으로 등장하는 확률 드롭 (강화석/초월석/유물 등) */
    public record CommonDrop(
            Material material,
            int amount,
            double chance,
            String displayName,
            java.util.List<String> lore,
            Integer customModelData,
            double shopPrice
    ) {
    }

    /**
     * 광부 상점 GUI 카탈로그 슬롯 정의.
     * <p>
     * display: GUI에 보일 아이템 (자유롭게 설정 — 로어에 {base-price}, {my-price},
     *   {my-amount}, {expected-total} 등 플레이스홀더 사용 가능)
     * sell:    실제 판매 매칭 기준 (material + customModelData + name)
     *          + unit-price (1개 단가). 인벤토리에서 일치하는 ItemStack을 모두 판매.
     */
    public record ShopGuiItem(
            String id,
            int slot,
            DisplayDef display,
            SellDef sell
    ) {
        public record DisplayDef(
                Material material,
                String name,
                java.util.List<String> lore,
                Integer customModelData
        ) {}

        public record SellDef(
                Material material,
                String name,
                Integer customModelData,
                double unitPrice
        ) {}
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
                Integer cmd = readCustomModelData(entry.get("custom-model-data"));
                double price = entry.get("shop-price") instanceof Number p ? p.doubleValue() : 0.0;
                drops.add(new OreDrop(mat, amount, name, lore, cmd, price));
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
            Integer cmd = readCustomModelData(entry.get("custom-model-data"));
            double price = entry.get("shop-price") instanceof Number p ? p.doubleValue() : 0.0;
            list.add(new CommonDrop(mat, amount, chance, name, lore, cmd, price));
        }
        return List.copyOf(list);
    }

    private static Integer readCustomModelData(Object raw) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        return null;
    }

    private java.util.Set<String> loadPassiveAllowedRegions() {
        ConfigurationSection root = config.getConfigurationSection("miner-passives");
        if (root == null) return java.util.Set.of();
        List<String> regions = root.getStringList("allowed-regions");
        if (regions == null || regions.isEmpty()) return java.util.Set.of();
        return java.util.Set.copyOf(regions);
    }

    private java.util.Set<String> loadMineAllowedRegions() {
        ConfigurationSection root = config.getConfigurationSection("worldguard");
        if (root == null) return java.util.Set.of();
        List<String> regions = root.getStringList("allowed-regions");
        if (regions == null || regions.isEmpty()) return java.util.Set.of();
        return java.util.Set.copyOf(regions);
    }

    private java.util.List<ShopGuiItem> loadShopGuiItems() {
        ConfigurationSection root = config.getConfigurationSection("shop-gui.items");
        if (root == null) return java.util.List.of();
        java.util.List<ShopGuiItem> list = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection itemSec = root.getConfigurationSection(id);
            if (itemSec == null) continue;
            int slot = itemSec.getInt("slot", -1);
            if (slot < 0) continue;

            ConfigurationSection dispSec = itemSec.getConfigurationSection("display");
            ConfigurationSection sellSec = itemSec.getConfigurationSection("sell");
            if (dispSec == null || sellSec == null) continue;

            Material dispMat = parseMaterial(dispSec.getString("material"));
            Material sellMat = parseMaterial(sellSec.getString("material"));
            if (dispMat == null || sellMat == null) continue;

            String dispName = dispSec.getString("name");
            List<String> dispLore = dispSec.getStringList("lore");
            Integer dispCmd = dispSec.contains("custom-model-data")
                    ? readCustomModelData(dispSec.getInt("custom-model-data", 0)) : null;

            String sellName = sellSec.getString("name");
            Integer sellCmd = sellSec.contains("custom-model-data")
                    ? readCustomModelData(sellSec.getInt("custom-model-data", 0)) : null;
            double unitPrice = sellSec.getDouble("unit-price", 0.0);

            list.add(new ShopGuiItem(
                    id,
                    slot,
                    new ShopGuiItem.DisplayDef(dispMat, dispName, dispLore, dispCmd),
                    new ShopGuiItem.SellDef(sellMat, sellName, sellCmd, unitPrice)
            ));
        }
        return java.util.List.copyOf(list);
    }

    /** shop-gui.size 기본 54 */
    public int getShopGuiSize() {
        int s = config.getInt("shop-gui.size", 54);
        // Inventory size는 9의 배수만 허용
        if (s % 9 != 0 || s < 9 || s > 54) return 54;
        return s;
    }

    /** shop-gui.layout.<key>.slot — 네비 슬롯 등 */
    public int getShopGuiLayoutSlot(String key, int defaultSlot) {
        return config.getInt("shop-gui.layout." + key + ".slot", defaultSlot);
    }

    public String getShopGuiLayoutName(String key, String defaultName) {
        return config.getString("shop-gui.layout." + key + ".name", defaultName);
    }

    public Material getShopGuiLayoutMaterial(String key, Material defaultMat) {
        String name = config.getString("shop-gui.layout." + key + ".material");
        if (name == null) return defaultMat;
        Material m = parseMaterial(name);
        return m != null ? m : defaultMat;
    }

    public List<String> getShopGuiLayoutLore(String key) {
        return config.getStringList("shop-gui.layout." + key + ".lore");
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

    public int getDynamiteFuseTicks() {
        return dynamiteFuseTicks;
    }

    public int getDynamiteCooldownTicks() {
        return dynamiteCooldownTicks;
    }

    /** 다이너마이트 해금 레벨 (기본 30) */
    public int getDynamiteUnlockLevel() {
        return config.getInt("dynamite.unlock-level", 30);
    }

    /**
     * 다이너마이트 폭발 한 변 크기 (블록 수, 기본 4 → 4×4×4 = 64 블록).
     * 구 키 explosion-radius (반경) 도 호환: r → size = r*2+1.
     */
    public int getDynamiteExplosionSize() {
        if (config.contains("dynamite.explosion-size")) {
            return Math.max(1, config.getInt("dynamite.explosion-size", 4));
        }
        if (config.contains("dynamite.explosion-radius")) {
            return Math.max(1, config.getInt("dynamite.explosion-radius", 1) * 2 + 1);
        }
        return 4;
    }

    /** SKILL 스탯 1레벨당 쿨다운 감소율 (0.0~1.0) */
    public double getDynamiteCooldownReductionPerSkill() {
        return config.getDouble("dynamite.cooldown-reduction-per-skill", 0.02);
    }

    /** 쿨다운 감소 상한 (예: 0.5 = 최대 50% 감소) */
    public double getDynamiteCooldownReductionCap() {
        return config.getDouble("dynamite.cooldown-reduction-cap", 0.5);
    }

    public String getDynamiteLevelDeniedMessage() {
        return config.getString("dynamite.level-denied-message",
                "&c[광부] &f직업 레벨 {level} 이상부터 다이너마이트를 사용할 수 있습니다.");
    }

    public String getDynamiteCooldownMessage() {
        return config.getString("dynamite.cooldown-message",
                "&c[광부] &f다이너마이트 쿨타임: {seconds}초");
    }

    public boolean isOverclockEnabled() {
        return overclockEnabled;
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

    // ── 오버클럭 신규 옵션들 ─────────────────────────────────────────────────
    public int getOverclockUnlockLevel() {
        if (config.contains("overclock.unlock-level")) {
            return config.getInt("overclock.unlock-level", 1);
        }
        return getOverclockMinJobLevel();
    }

    // SKILL 스탯 효과
    public double getOverclockCooldownReductionPerSkill() {
        return config.getDouble("overclock.cooldown-reduction-per-skill", 0.02);
    }
    public double getOverclockCooldownReductionCap() {
        return config.getDouble("overclock.cooldown-reduction-cap", 0.5);
    }
    public double getOverclockDurationBonusPerSkill() {
        return config.getDouble("overclock.duration-bonus-per-skill", 0.02);
    }
    public double getOverclockDurationBonusCap() {
        return config.getDouble("overclock.duration-bonus-cap", 1.0);
    }

    // 발동 사운드
    public String getOverclockActivationSound() {
        return config.getString("overclock.activation-sound", "ENTITY_BLAZE_SHOOT");
    }
    public float getOverclockActivationSoundVolume() {
        return (float) config.getDouble("overclock.activation-sound-volume", 1.0);
    }
    public float getOverclockActivationSoundPitch() {
        return (float) config.getDouble("overclock.activation-sound-pitch", 1.0);
    }

    // 80레벨 메카닉 (지속시간 연장)
    public int getOverclockExtensionUnlockLevel() {
        return config.getInt("overclock.extension-unlock-level", 80);
    }
    public double getOverclockExtensionBaseChance() {
        return config.getDouble("overclock.extension-base-chance", 0.5);
    }
    public double getOverclockExtensionChancePerSkill() {
        return config.getDouble("overclock.extension-chance-per-skill", 0.005);
    }
    public double getOverclockExtensionChanceCap() {
        return config.getDouble("overclock.extension-chance-cap", 1.0);
    }
    public int getOverclockExtensionBaseTicks() {
        if (config.contains("overclock.extension-base-ticks")) {
            return config.getInt("overclock.extension-base-ticks", 100);
        }
        return config.getInt("overclock.extension-ticks", 100);
    }
    public int getOverclockExtensionTicksPerSkill() {
        return config.getInt("overclock.extension-ticks-per-skill", 2);
    }
    public int getOverclockExtensionTicksCap() {
        return config.getInt("overclock.extension-ticks-cap", 200);
    }
    public String getOverclockExtensionSuccessMessage() {
        return config.getString("overclock.extension-success-message",
                "&e[광부] &f오버클럭 &a+{seconds}초 &f연장!");
    }
    public String getOverclockExtensionFailureMessage() {
        return config.getString("overclock.extension-failure-message",
                "&7[광부] &f연장 실패 &8({chance}%)");
    }

    /** 가벼운 발걸음 해금 레벨 (기본 50) */
    public int getLightFootstepsUnlockLevel() {
        org.bukkit.configuration.ConfigurationSection root =
                config.getConfigurationSection("miner-passives.light-footsteps");
        if (root == null) return 50;
        return root.getInt("unlock-level", 50);
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

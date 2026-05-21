package kr.minefarm.job.jobminer.kit;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code starter-kit.items} 템플릿을 읽어 직업 선택 시 지급한다.
 *
 * <h3>지원 설정 (각 아이템마다)</h3>
 * <pre>
 * - material: DIAMOND_PICKAXE          # 필수 — Bukkit Material 이름
 *   amount: 1                          # 기본 1, 최대 64
 *   name: "&6다이아 광부 곡괭이"         # 표시 이름 (&색코드 지원)
 *   lore:                              # 설명 (줄마다 & 색코드 지원)
 *     - "&7리젠 광산 전용 곡괭이"
 *     - "&e효율 III &7강화"
 *   enchantments:                      # 인챈트 (이름: 레벨)
 *     efficiency: 3
 *     unbreaking: 3
 *     fortune: 2
 *     mending: 1
 *   slot: 0                            # 인벤토리 슬롯 직접 지정 (없으면 자동 배치)
 *                                      # 특수: "hand" = 주 손, "offhand" = 보조 손
 *                                      #        "helmet","chestplate","leggings","boots"
 * </pre>
 */
public final class StarterKitService {

    private static final Map<String, String> ENCHANT_ALIASES;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("DIG_SPEED", "efficiency");
        m.put("EFFICIENCY", "efficiency");
        m.put("DURABILITY", "unbreaking");
        m.put("UNBREAKING", "unbreaking");
        m.put("LOOT_BONUS_BLOCKS", "fortune");
        m.put("FORTUNE", "fortune");
        m.put("LOOTING", "looting");
        m.put("MENDING", "mending");
        m.put("SILK_TOUCH", "silk_touch");
        m.put("SILKTOUCH", "silk_touch");
        m.put("SHARPNESS", "sharpness");
        m.put("PROTECTION", "protection");
        m.put("FIRE_PROTECTION", "fire_protection");
        m.put("BLAST_PROTECTION", "blast_protection");
        m.put("FEATHER_FALLING", "feather_falling");
        m.put("THORNS", "thorns");
        ENCHANT_ALIASES = Map.copyOf(m);
    }

    private record KitEntry(ItemStack stack, String slot) {}

    private final List<KitEntry> entries;
    private final java.util.logging.Logger logger;

    public StarterKitService(JobMinerConfig minerConfig, java.util.logging.Logger logger) {
        this.logger = logger;
        this.entries = parseEntries(minerConfig, logger);
    }

    public StarterKitService(JobMinerConfig minerConfig) {
        this(minerConfig, java.util.logging.Logger.getLogger("JobMiner"));
    }

    /**
     * 모든 킷 아이템을 플레이어에게 지급한다.
     * slot이 지정된 아이템은 해당 슬롯에, 없으면 addItem으로 자동 배치.
     * 인벤토리가 꽉 차면 발밑 드롭.
     */
    public void equipStarterKit(Player player) {
        if (!player.isOnline() || entries.isEmpty()) return;
        PlayerInventory inv = player.getInventory();

        for (KitEntry entry : entries) {
            ItemStack give = entry.stack().clone();
            if (give.getType().isAir() || give.getAmount() <= 0) continue;

            boolean placed = tryPlaceInSlot(inv, give, entry.slot());
            if (!placed) {
                // 지정 슬롯 실패 or 슬롯 없음 → 자동 배치
                HashMap<Integer, ItemStack> leftover = inv.addItem(give);
                leftover.values().forEach(overflow ->
                        player.getWorld().dropItemNaturally(player.getLocation(), overflow));
            }
        }
    }

    private boolean tryPlaceInSlot(PlayerInventory inv, ItemStack give, String slot) {
        if (slot == null || slot.isBlank()) return false;
        String s = slot.toLowerCase(Locale.ROOT);
        if (s.equals("hand") || s.equals("mainhand")) {
            inv.setItemInMainHand(give); return true;
        } else if (s.equals("offhand")) {
            inv.setItemInOffHand(give); return true;
        } else if (s.equals("helmet") || s.equals("head")) {
            inv.setHelmet(give); return true;
        } else if (s.equals("chestplate") || s.equals("chest")) {
            inv.setChestplate(give); return true;
        } else if (s.equals("leggings") || s.equals("legs")) {
            inv.setLeggings(give); return true;
        } else if (s.equals("boots") || s.equals("feet")) {
            inv.setBoots(give); return true;
        } else {
            try {
                int idx = Integer.parseInt(s.trim());
                if (idx >= 0 && idx < inv.getSize()) {
                    ItemStack existing = inv.getItem(idx);
                    if (existing == null || existing.getType().isAir()) {
                        inv.setItem(idx, give);
                        return true;
                    }
                }
            } catch (NumberFormatException ignored) {}
            return false;
        }
    }

    // ── 파싱 ──────────────────────────────────────────────────────────────────

    private static List<KitEntry> parseEntries(JobMinerConfig minerConfig,
                                               java.util.logging.Logger logger) {
        List<KitEntry> out = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection root = minerConfig.getStarterKitSection();
        if (root == null) return out;
        List<Map<?, ?>> rows = root.getMapList("items");
        if (rows == null || rows.isEmpty()) return out;
        for (Map<?, ?> row : rows) {
            ItemStack stack = parseItemRow(row, logger);
            if (stack == null) continue;
            String slot = row.containsKey("slot") ? String.valueOf(row.get("slot")).trim() : null;
            out.add(new KitEntry(stack, slot));
        }
        return out;
    }

    private static ItemStack parseItemRow(Map<?, ?> row, java.util.logging.Logger logger) {
        // material (필수)
        Object matObj = row.get("material");
        if (matObj == null) return null;
        Material material;
        try {
            material = Material.valueOf(String.valueOf(matObj).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("[JobMiner] 스타터킷: 알 수 없는 머티리얼 '" + matObj + "' — 무시됨");
            return null;
        }

        // amount
        int amount = 1;
        Object amtObj = row.get("amount");
        if (amtObj instanceof Number n) amount = Math.max(1, Math.min(64, n.intValue()));
        else if (amtObj != null) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(String.valueOf(amtObj).trim()))); }
            catch (NumberFormatException ignored) {}
        }

        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        // name
        Object nameObj = row.get("name");
        if (nameObj != null) {
            String raw = String.valueOf(nameObj);
            if (!raw.isBlank()) meta.displayName(legacy(raw));
        }

        // lore
        Object loreObj = row.get("lore");
        if (loreObj instanceof List<?> loreList && !loreList.isEmpty()) {
            meta.lore(loreList.stream()
                    .map(l -> legacy(String.valueOf(l)))
                    .toList());
        }

        // enchantments
        Object enchObj = row.get("enchantments");
        if (enchObj instanceof Map<?, ?> enchMap) {
            for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                String key = String.valueOf(e.getKey()).trim();
                int level = 1;
                if (e.getValue() instanceof Number n) level = Math.max(1, n.intValue());
                else if (e.getValue() != null) {
                    try { level = Math.max(1, Integer.parseInt(String.valueOf(e.getValue()).trim())); }
                    catch (NumberFormatException ignored) {}
                }
                final int finalLevel = level;
                resolveEnchantment(key).ifPresentOrElse(
                        ench -> meta.addEnchant(ench, finalLevel, true),
                        () -> logger.warning("[JobMiner] 스타터킷: 알 수 없는 인챈트 키 '" + key + "' — 무시됨")
                );
            }
        }

        // custom-model-data (선택)
        Object cmdObj = row.get("custom-model-data");
        if (cmdObj instanceof Number n) meta.setCustomModelData(n.intValue());

        // unbreakable (선택)
        Object unbreakObj = row.get("unbreakable");
        if (Boolean.TRUE.equals(unbreakObj) || "true".equalsIgnoreCase(String.valueOf(unbreakObj))) {
            meta.setUnbreakable(true);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private static Optional<Enchantment> resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String key = ENCHANT_ALIASES.getOrDefault(upper, raw.trim().toLowerCase(Locale.ROOT).replace('-', '_'));
        if (!key.contains(":")) {
            return Optional.ofNullable(Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key)));
        }
        String[] parts = key.split(":", 2);
        return Optional.ofNullable(Registry.ENCHANTMENT.get(new NamespacedKey(parts[0], parts[1])));
    }
}

package kr.minefarm.job.jobminer.kit;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
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
 * {@code jobminer/config.yml} 의 {@code starter-kit} 템플릿을 읽어 직업 선택 시 지급한다.
 * <ul>
 *     <li>지급 시 반드시 {@link ItemStack#clone()} 으로 복제해 템플릿 리스트가 변형되지 않게 한다.</li>
 *     <li>{@link PlayerInventory#addItem(ItemStack...)} 반환 {@code Map<Integer, ItemStack>}에 남은 스택만
 *     {@code dropItemNaturally} 한다 (인벤에 들어간 만큼만 처리).</li>
 * </ul>
 * <p>
 * {@link #equipStarterKit} 은 JobCore에서 DB 비동기 로드 후 {@code runTask}로 호출되는 것을 전제로 한다 (메인 스레드 전용).
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
        ENCHANT_ALIASES = Map.copyOf(m);
    }

    private final List<ItemStack> templates;

    public StarterKitService(JobMinerConfig minerConfig) {
        this.templates = parseTemplates(minerConfig);
    }

    /**
     * 템플릿마다 {@link ItemStack#clone()} 후 {@link PlayerInventory#addItem(ItemStack...)} 으로 넣고,
     * 반환 맵에만 담긴 초과분을 {@code dropItemNaturally} 한다.
     */
    public void equipStarterKit(Player player) {
        if (!player.isOnline() || templates.isEmpty()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack template : templates) {
            ItemStack give = template.clone();
            if (give.getType().isAir() || give.getAmount() <= 0) {
                continue;
            }
            HashMap<Integer, ItemStack> didNotFit = inventory.addItem(give);
            if (didNotFit.isEmpty()) {
                continue;
            }
            for (ItemStack overflow : didNotFit.values()) {
                if (overflow == null || overflow.getType().isAir() || overflow.getAmount() <= 0) {
                    continue;
                }
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
    }

    private static List<ItemStack> parseTemplates(JobMinerConfig minerConfig) {
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection root = minerConfig.getStarterKitSection();
        if (root == null) {
            return out;
        }
        List<Map<?, ?>> rows = root.getMapList("items");
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (Map<?, ?> row : rows) {
            ItemStack stack = parseItemRow(row);
            if (stack != null) {
                out.add(stack);
            }
        }
        return out;
    }

    private static ItemStack parseItemRow(Map<?, ?> row) {
        Object matObj = row.get("material");
        if (matObj == null) {
            return null;
        }
        Material material;
        try {
            material = Material.valueOf(String.valueOf(matObj).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        int amount = 1;
        Object amountObj = row.get("amount");
        if (amountObj instanceof Number number) {
            amount = Math.max(1, Math.min(64, number.intValue()));
        } else if (amountObj != null) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(String.valueOf(amountObj).trim())));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }

        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        Object nameObj = row.get("name");
        if (nameObj != null) {
            String raw = String.valueOf(nameObj);
            if (!raw.isBlank()) {
                meta.displayName(legacy(raw));
            }
        }

        Object loreObj = row.get("lore");
        if (loreObj instanceof List<?> loreList && !loreList.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Object line : loreList) {
                lines.add(legacy(String.valueOf(line)));
            }
            meta.lore(lines);
        }

        Object enchObj = row.get("enchantments");
        if (enchObj instanceof Map<?, ?> enchMap) {
            for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                int level = 1;
                if (entry.getValue() instanceof Number number) {
                    level = Math.max(1, number.intValue());
                } else if (entry.getValue() != null) {
                    try {
                        level = Math.max(1, Integer.parseInt(String.valueOf(entry.getValue()).trim()));
                    } catch (NumberFormatException ignored) {
                        level = 1;
                    }
                }
                resolveEnchantment(key).ifPresent(ench -> meta.addEnchant(ench, level, true));
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private static Optional<Enchantment> resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String minecraftKey = ENCHANT_ALIASES.getOrDefault(upper, raw.trim().toLowerCase(Locale.ROOT).replace('-', '_'));
        if (!minecraftKey.contains(":")) {
            Enchantment byKey = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(minecraftKey.toLowerCase(Locale.ROOT)));
            return Optional.ofNullable(byKey);
        }
        String[] parts = minecraftKey.split(":", 2);
        Enchantment byNs = Registry.ENCHANTMENT.get(new NamespacedKey(parts[0], parts[1]));
        return Optional.ofNullable(byNs);
    }
}

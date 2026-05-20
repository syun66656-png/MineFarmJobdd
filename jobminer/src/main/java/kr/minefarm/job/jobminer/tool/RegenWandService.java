package kr.minefarm.job.jobminer.tool;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 광산 관리 완드 생성·식별.
 */
public final class RegenWandService {

    private final NamespacedKey wandKey;
    private final Material material;
    private final Component displayName;
    private final List<Component> lore;

    public RegenWandService(JavaPlugin plugin, JobMinerConfig config) {
        this.wandKey = new NamespacedKey(plugin, "regen_wand");
        this.material = config.getRegenWandMaterial();
        this.displayName = legacy(config.getRegenWandDisplayName());
        this.lore = config.getRegenWandLore().stream().map(RegenWandService::legacy).toList();
    }

    public ItemStack createWand() {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(displayName);
        if (!lore.isEmpty()) {
            meta.lore(new ArrayList<>(lore));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isRegenWand(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}

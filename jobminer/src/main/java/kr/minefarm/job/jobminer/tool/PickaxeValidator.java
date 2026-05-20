package kr.minefarm.job.jobminer.tool;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * config 키워드 포함 곡괭이 검증 (stripColor 후 부분 일치).
 */
public final class PickaxeValidator {

    private final List<String> keywords;

    public PickaxeValidator(JobMinerConfig config) {
        this.keywords = List.copyOf(config.getAllowedPickaxeKeywords());
    }

    public boolean isValidPickaxe(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        String plain = toPlainText(hand);
        if (plain.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (plain.contains(strip(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String toPlainText(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack.getType().name();
        }
        if (meta.hasDisplayName()) {
            return strip(LegacyComponentSerializer.legacySection().serialize(meta.displayName()));
        }
        if (meta.hasItemName()) {
            return strip(meta.getItemName());
        }
        return stack.getType().name();
    }

    private static String strip(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(input.replace('&', '§'));
    }
}

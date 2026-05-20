package kr.minefarm.job.jobminer.tool;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * config 키워드 포함 곡괭이 검증.
 * <p>
 * 반드시 {@link #ALLOWED_TYPES} 에 포함된 머티리얼이어야 하며,
 * 그 다음 표시 이름에 허용된 키워드가 있어야 한다.
 * 머티리얼 검사 없이 이름만 검사하면 막대기 등 임의 아이템 리네임으로
 * 리젠 광산을 채굴하는 익스플로잇이 가능하므로 반드시 선행 검사한다.
 */
public final class PickaxeValidator {

    /**
     * 허용된 곡괭이 머티리얼 화이트리스트.
     * 이 집합에 없는 아이템은 이름에 상관없이 무조건 거부한다.
     */
    private static final Set<Material> ALLOWED_TYPES = Set.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    private final List<String> keywords;

    public PickaxeValidator(JobMinerConfig config) {
        this.keywords = List.copyOf(config.getAllowedPickaxeKeywords());
    }

    public boolean isValidPickaxe(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }

        // ① 머티리얼 화이트리스트 — 이름만 바꾼 아이템 익스플로잇 차단
        if (!ALLOWED_TYPES.contains(hand.getType())) {
            return false;
        }

        // ② 키워드 없으면 머티리얼만으로 통과
        if (keywords.isEmpty()) {
            return true;
        }

        // ③ 표시 이름 키워드 검사 (색상 코드 제거 후 부분 일치)
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

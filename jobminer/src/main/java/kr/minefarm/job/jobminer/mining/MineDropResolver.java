package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 채굴 드롭 해석.
 * <ul>
 *   <li>{@code ore-drops}: 광물 Material마다 다른 지정 드롭</li>
 *   <li>{@code common-drops}: 모든 광물에서 공통 확률 드롭 (강화석/초월석/유물)</li>
 *   <li>(deprecated) {@code guaranteed-drops}: 광물 무관 100% 드롭 — 호환 유지</li>
 *   <li>(deprecated) {@code special-drops}: 광물 무관 확률 드롭 — 호환 유지</li>
 * </ul>
 */
public final class MineDropResolver {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final JobMinerConfig config;

    public MineDropResolver(JobMinerConfig config) {
        this.config = config;
    }

    /** 채굴된 블록 Material에 따른 드롭 + 공통 확률 드롭 + (deprecated) 전역 드롭 */
    public List<ItemStack> resolveMiningDrops(Material blockMaterial) {
        List<ItemStack> items = new ArrayList<>();

        // ① 광물별 지정 드롭
        for (JobMinerConfig.OreDrop drop : config.getOreDropsFor(blockMaterial)) {
            items.add(buildOreDrop(drop));
        }

        // ② 공통 확률 드롭 (강화석/초월석/유물)
        for (JobMinerConfig.CommonDrop drop : config.getCommonDrops()) {
            if (ThreadLocalRandom.current().nextDouble() < drop.chance()) {
                items.add(buildCommonDrop(drop));
            }
        }

        // ③ (호환) 광물 무관 guaranteed-drops
        for (Map.Entry<Material, Integer> entry : config.getGuaranteedDrops().entrySet()) {
            if (entry.getValue() > 0) {
                items.add(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }

        // ④ (호환) 광물 무관 special-drops
        for (JobMinerConfig.SpecialDrop special : config.getSpecialDrops()) {
            if (ThreadLocalRandom.current().nextDouble() < special.chance()) {
                items.add(new ItemStack(special.material(), special.amount()));
            }
        }
        return items;
    }

    /**
     * 광물별 지정 드롭만 반환 (RELIC 보너스 드롭 계산 전용).
     * 공통 확률 드롭은 보너스 대상이 아님.
     */
    public List<ItemStack> resolveGuaranteedDropsOnly(Material blockMaterial) {
        List<ItemStack> items = new ArrayList<>();
        for (JobMinerConfig.OreDrop drop : config.getOreDropsFor(blockMaterial)) {
            items.add(buildOreDrop(drop));
        }
        for (Map.Entry<Material, Integer> entry : config.getGuaranteedDrops().entrySet()) {
            if (entry.getValue() > 0) {
                items.add(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
        return items;
    }

    /** OreDrop / CommonDrop 공통: customModelData 포함 ItemStack 생성 */
    public static ItemStack buildOreDrop(JobMinerConfig.OreDrop drop) {
        return buildItem(drop.material(), drop.amount(), drop.displayName(), drop.lore(), drop.customModelData());
    }

    public static ItemStack buildCommonDrop(JobMinerConfig.CommonDrop drop) {
        return buildItem(drop.material(), drop.amount(), drop.displayName(), drop.lore(), drop.customModelData());
    }

    private static ItemStack buildItem(
            Material material,
            int amount,
            String displayName,
            List<String> lore,
            Integer customModelData
    ) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        boolean hasName = displayName != null && !displayName.isBlank();
        boolean hasLore = lore != null && !lore.isEmpty();
        boolean hasCmd = customModelData != null && customModelData > 0;
        if (!hasName && !hasLore && !hasCmd) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (hasName) {
            meta.displayName(AMP.deserialize(displayName));
        }
        if (hasLore) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(AMP.deserialize(line == null ? "" : line));
            }
            meta.lore(components);
        }
        if (hasCmd) {
            meta.setCustomModelData(customModelData);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}

package kr.minefarm.job.jobcore.boost;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 경험치 부스트 쿠폰 — 아이템 PDC 에 배율/지속분(분) 저장.
 * 사용 시 우클릭으로 적용, 1개 소모.
 */
public final class ExperienceBoostItem {

    private final NamespacedKey multiplierKey;
    private final NamespacedKey minutesKey;

    public ExperienceBoostItem(JavaPlugin plugin) {
        this.multiplierKey = new NamespacedKey(plugin, "boost_multiplier");
        this.minutesKey = new NamespacedKey(plugin, "boost_minutes");
    }

    /** 손에 든 아이템을 부스트 쿠폰으로 표시 (PDC 부여). 표시 이름/로어도 보강. */
    public boolean stampItem(ItemStack stack, double multiplier, int minutes) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
        pdc.set(minutesKey, PersistentDataType.INTEGER, minutes);
        stack.setItemMeta(meta);
        return true;
    }

    /** PDC 가 있는 쿠폰인지 + 배율/분 추출. 아니면 null 반환. */
    public BoostData read(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(multiplierKey, PersistentDataType.DOUBLE)) return null;
        if (!pdc.has(minutesKey, PersistentDataType.INTEGER)) return null;
        double mul = pdc.get(multiplierKey, PersistentDataType.DOUBLE);
        int min = pdc.get(minutesKey, PersistentDataType.INTEGER);
        return new BoostData(mul, min);
    }

    public record BoostData(double multiplier, int minutes) {}
}

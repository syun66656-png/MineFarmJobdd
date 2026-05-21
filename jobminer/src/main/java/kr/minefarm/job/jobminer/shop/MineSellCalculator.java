package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.inventory.ItemStack;

/**
 * 판매가 계산: total = base + (base × bonus-multiplier × sellLevel).
 */
public final class MineSellCalculator {

    private final JobMinerConfig config;

    public MineSellCalculator(JobMinerConfig config) {
        this.config = config;
    }

    /**
     * ItemStack 단위 가격 계산.
     * ore-drops / common-drops 의 shop-price 매칭 시도 → 매칭 실패 시 0 반환.
     * total = unit × amount × (1 + bonus-multiplier × SELL 레벨)
     */
    public double calculateTotalPrice(ItemStack stack, int sellStatLevel) {
        if (stack == null || stack.getType().isAir()) {
            return 0D;
        }
        double unitBase = config.findShopPriceForItem(stack);
        if (unitBase <= 0D) {
            return 0D;
        }
        double base = unitBase * stack.getAmount();
        double bonus = base * config.getSellBonusMultiplier() * sellStatLevel;
        return base + bonus;
    }

    public String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }
}

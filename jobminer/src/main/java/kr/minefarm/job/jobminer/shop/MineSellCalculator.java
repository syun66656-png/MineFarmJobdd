package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 판매가 계산: total = base + (base × bonus-multiplier × sellLevel).
 */
public final class MineSellCalculator {

    private final JobMinerConfig config;

    public MineSellCalculator(JobMinerConfig config) {
        this.config = config;
    }

    public double calculateTotalPrice(Material material, int amount, int sellStatLevel) {
        if (amount <= 0) {
            return 0D;
        }
        double unitBase = config.getShopBasePrice(material);
        if (unitBase <= 0D) {
            return 0D;
        }
        double base = unitBase * amount;
        double bonus = base * config.getSellBonusMultiplier() * sellStatLevel;
        return base + bonus;
    }

    public double calculateTotalPrice(ItemStack stack, int sellStatLevel) {
        if (stack == null || stack.getType().isAir()) {
            return 0D;
        }
        // ore-drop / common-drop 매칭 (custom-model-data + name) 우선 시도
        double oreDropUnit = config.findShopPriceForItem(stack);
        if (oreDropUnit > 0) {
            double base = oreDropUnit * stack.getAmount();
            double bonus = base * config.getSellBonusMultiplier() * sellStatLevel;
            return base + bonus;
        }
        if (oreDropUnit == 0) return 0D; // ore-drop에 명시적으로 미판매 지정된 아이템
        // 매칭 실패 → shop-prices Material 기반 fallback
        return calculateTotalPrice(stack.getType(), stack.getAmount(), sellStatLevel);
    }

    public String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }
}

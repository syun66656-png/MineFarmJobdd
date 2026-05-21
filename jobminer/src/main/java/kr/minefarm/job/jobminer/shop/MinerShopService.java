package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.VaultEconomyBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 광부 상점 판매 비즈니스 로직.
 * <p>
 * GUI와 커맨드 양쪽에서 사용한다.
 * Vault 입금까지 처리하므로 모든 메서드는 메인 스레드에서 호출해야 한다.
 */
public final class MinerShopService {

    private final JobMinerConfig config;
    private final MineSellCalculator calculator;
    private final VaultEconomyBridge economy;

    public MinerShopService(
            JobMinerConfig config,
            MineSellCalculator calculator,
            VaultEconomyBridge economy
    ) {
        this.config = config;
        this.calculator = calculator;
        this.economy = economy;
    }

    /** Vault Economy 사용 가능 여부 확인 */
    public boolean isEconomyAvailable() {
        return economy.isAvailable();
    }

    /**
     * 플레이어 인벤토리에서 {@code material} 종류를 모두 판매한다.
     *
     * @return 판매 결과 (수익 0이면 판매 실패 / 아이템 없음)
     */
    public SellResult sellAll(Player player, Material material, PlayerJobProfile profile) {
        int sellStat = profile.getStatLevel(StatType.SELL);
        double unitBasePrice = config.getShopBasePrice(material);
        if (unitBasePrice <= 0) {
            return SellResult.noPrice(material);
        }

        // 인벤에서 해당 머티리얼 스택 수집
        List<ItemStack> toRemove = new ArrayList<>();
        int totalAmount = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != material) continue;
            toRemove.add(stack);
            totalAmount += stack.getAmount();
        }
        if (totalAmount == 0) {
            return SellResult.noItems(material);
        }

        double totalPrice = calculator.calculateTotalPrice(material, totalAmount, sellStat);
        if (!economy.deposit(player, totalPrice)) {
            return SellResult.vaultError(material, totalAmount, totalPrice);
        }

        // 입금 성공 후 아이템 제거
        for (ItemStack stack : toRemove) {
            player.getInventory().remove(stack);
        }
        player.updateInventory();
        return SellResult.success(material, totalAmount, totalPrice);
    }

    /**
     * 인벤토리에서 가격 있는 모든 아이템(ore-drop 커스텀 아이템 포함)을 한 번에 판매한다.
     * <p>
     * 매칭 우선순위:
     *   1. ore-drops / common-drops 의 (material + custom-model-data + name) 매칭 → shop-price 사용
     *   2. 매칭 실패 시 shop-prices 의 Material 기반 단가
     */
    public BulkSellResult sellAllPriced(Player player, PlayerJobProfile profile) {
        int sellStat = profile.getStatLevel(StatType.SELL);

        List<ItemStack> toRemove = new ArrayList<>();
        Map<Material, Integer> amountsForResult = new LinkedHashMap<>();
        double grand = 0.0;

        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;

            double unitBase = resolveUnitPrice(stack);
            if (unitBase <= 0) continue;

            int amount = stack.getAmount();
            double total = unitBase * amount * (1.0 + config.getSellBonusMultiplier() * sellStat);

            grand += total;
            amountsForResult.merge(stack.getType(), amount, Integer::sum);
            toRemove.add(stack);
        }

        if (toRemove.isEmpty()) {
            return new BulkSellResult(0, 0.0, List.of());
        }

        if (!economy.deposit(player, grand)) {
            return new BulkSellResult(amountsForResult.size(), 0.0, List.of());
        }

        for (ItemStack stack : toRemove) {
            player.getInventory().remove(stack);
        }
        player.updateInventory();

        List<SellResult> results = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : amountsForResult.entrySet()) {
            results.add(SellResult.success(entry.getKey(), entry.getValue(), 0.0));
        }
        return new BulkSellResult(amountsForResult.size(), grand, results);
    }

    /**
     * 인벤토리 ItemStack의 단가 결정 (ore-drop 우선, Material fallback).
     * 반환 값은 SELL스탯 적용 전 base 단가.
     */
    private double resolveUnitPrice(ItemStack stack) {
        // 1. ore-drop / common-drop 매칭 (커스텀 모델·이름 포함)
        double oreDropPrice = config.findShopPriceForItem(stack);
        if (oreDropPrice > 0) return oreDropPrice;
        if (oreDropPrice == 0) return 0; // 매칭됐지만 미판매로 명시
        // 2. shop-prices Material 기준 fallback
        return config.getShopBasePrice(stack.getType());
    }

    /**
     * 가격이 있는 머티리얼 목록 (상점 GUI 카탈로그 표시용).
     */
    public List<Material> getPricedMaterials() {
        return config.getShopPricedMaterials();
    }

    /**
     * 현재 SELL 스탯 기준 단가 문자열.
     */
    public String formatUnitPrice(Material material, int sellStat) {
        double price = calculator.calculateTotalPrice(material, 1, sellStat);
        return calculator.formatPrice(price);
    }

    // ── 결과 레코드 ───────────────────────────────────────────────────────────

    public record SellResult(
            Status status,
            Material material,
            int amount,
            double price
    ) {
        public enum Status { SUCCESS, NO_PRICE, NO_ITEMS, VAULT_ERROR }

        static SellResult success(Material m, int amount, double price) {
            return new SellResult(Status.SUCCESS, m, amount, price);
        }

        static SellResult noPrice(Material m) {
            return new SellResult(Status.NO_PRICE, m, 0, 0);
        }

        static SellResult noItems(Material m) {
            return new SellResult(Status.NO_ITEMS, m, 0, 0);
        }

        static SellResult vaultError(Material m, int amount, double price) {
            return new SellResult(Status.VAULT_ERROR, m, amount, price);
        }

        public boolean isSuccess() { return status == Status.SUCCESS; }
    }

    public record BulkSellResult(int materialCount, double totalGold, List<SellResult> details) {
        public boolean isSuccess() { return totalGold > 0; }
    }
}

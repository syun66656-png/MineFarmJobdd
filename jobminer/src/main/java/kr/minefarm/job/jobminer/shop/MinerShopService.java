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
     * 인벤토리에서 가격 있는 모든 광석을 한 번에 판매한다.
     */
    public BulkSellResult sellAllPriced(Player player, PlayerJobProfile profile) {
        int sellStat = profile.getStatLevel(StatType.SELL);

        // Material별로 수량 집계
        Map<Material, Integer> amounts = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            double price = config.getShopBasePrice(stack.getType());
            if (price <= 0) continue;
            amounts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        if (amounts.isEmpty()) {
            return new BulkSellResult(0, 0.0, List.of());
        }

        double grand = 0.0;
        List<SellResult> results = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : amounts.entrySet()) {
            double price = calculator.calculateTotalPrice(entry.getKey(), entry.getValue(), sellStat);
            grand += price;
            results.add(SellResult.success(entry.getKey(), entry.getValue(), price));
        }

        if (!economy.deposit(player, grand)) {
            return new BulkSellResult(amounts.size(), 0.0, List.of());
        }

        // 가격 있는 아이템 전체 제거
        for (Material mat : amounts.keySet()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == mat) {
                    player.getInventory().remove(stack);
                }
            }
        }
        player.updateInventory();
        return new BulkSellResult(amounts.size(), grand, results);
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

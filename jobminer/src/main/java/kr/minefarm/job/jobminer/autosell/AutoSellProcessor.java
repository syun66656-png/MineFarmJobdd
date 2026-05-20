package kr.minefarm.job.jobminer.autosell;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.VaultEconomyBridge;
import kr.minefarm.job.jobminer.shop.MineSellCalculator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 자동판매 ON 시 {@link MineSellCalculator}·Vault로 가격 있는 스택만 입금하고, 나머지는 인벤용 목록으로 반환한다.
 */
public final class AutoSellProcessor {

    private final JobMinerConfig config;
    private final MineSellCalculator sellCalculator;
    private final VaultEconomyBridge economy;

    public AutoSellProcessor(
            JobMinerConfig config,
            MineSellCalculator sellCalculator,
            VaultEconomyBridge economy
    ) {
        this.config = config;
        this.sellCalculator = sellCalculator;
        this.economy = economy;
    }

    public boolean shouldAttemptAutoSell(PlayerJobProfile profile) {
        return profile != null && profile.isAutoSellEnabled();
    }

    /**
     * {@link MineSellCalculator} 공식으로 가격이 나는 스택만 합산해 Vault에 입금한다.
     * <ul>
     *     <li>가격 0(가격표 없음)인 스택은 판매하지 않고 {@code stacksForInventory}에 넣는다.</li>
     *     <li>판매할 총액이 0이면 입금 없이 전부 {@code stacksForInventory}.</li>
     *     <li>입금 실패 시에도 전부 {@code stacksForInventory}(복제)로 돌려 플레이어에게 지급 가능하게 한다.</li>
     * </ul>
     */
    public PricedAutoSellResult sellPricedStacks(Player player, PlayerJobProfile profile, List<ItemStack> drops) {
        int sellLevel = profile.getStatLevel(StatType.SELL);
        List<ItemStack> unpriced = new ArrayList<>();
        double total = 0D;
        StringBuilder summary = new StringBuilder();

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            ItemStack copy = drop.clone();
            double price = sellCalculator.calculateTotalPrice(copy, sellLevel);
            if (price <= 0D) {
                unpriced.add(copy);
            } else {
                total += price;
                if (!summary.isEmpty()) {
                    summary.append(", ");
                }
                summary.append(copy.getType().name()).append(" x").append(copy.getAmount());
            }
        }

        if (total <= 0D) {
            return new PricedAutoSellResult(0D, cloneAllNonEmpty(drops));
        }

        if (!economy.deposit(player, total)) {
            return new PricedAutoSellResult(0D, cloneAllNonEmpty(drops));
        }

        if (config.isAutoSellNotifyPlayer()) {
            String message = config.getAutoSellMessage()
                    .replace("{summary}", summary.toString())
                    .replace("{total_price}", sellCalculator.formatPrice(total));
            player.sendMessage(message.replace('&', '§'));
        }

        return new PricedAutoSellResult(total, unpriced);
    }

    private static List<ItemStack> cloneAllNonEmpty(List<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            out.add(drop.clone());
        }
        return out;
    }
}

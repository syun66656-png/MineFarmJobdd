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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 자동판매 ON 시 AUTO_SELL 스탯 기반 확률로 드롭을 판매한다.
 * <p>
 * 판매 확률: {@code min(max-chance, base-chance + chance-per-level × AUTO_SELL스탯)}
 * 확률 판정을 통과한 스택 중 가격이 있는 것만 Vault 에 입금하고,
 * 나머지(확률 실패 + 가격 없음 + 입금 실패)는 인벤토리용 목록으로 반환한다.
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
     * AUTO_SELL 스탯 기반 확률로 드롭을 판매한다.
     * <ul>
     *   <li>확률 판정 통과 + 가격 있음 → Vault 입금 대상</li>
     *   <li>그 외(확률 실패, 가격 없음, 입금 실패) → 인벤토리용 반환</li>
     * </ul>
     */
    public PricedAutoSellResult sellPricedStacks(Player player, PlayerJobProfile profile, List<ItemStack> drops) {
        int sellLevel = profile.getStatLevel(StatType.SELL);
        int autoSellLevel = profile.getStatLevel(StatType.AUTO_SELL);

        // AUTO_SELL 스탯 기반 판매 확률 계산
        double sellChance = Math.min(
                config.getAutoSellMaxChance(),
                config.getAutoSellBaseChance() + config.getAutoSellChancePerLevel() * autoSellLevel
        );

        List<ItemStack> toSell = new ArrayList<>();
        List<ItemStack> unpriced = new ArrayList<>();
        double total = 0D;
        StringBuilder summary = new StringBuilder();

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            ItemStack copy = drop.clone();

            // ① AUTO_SELL 확률 판정
            if (ThreadLocalRandom.current().nextDouble() >= sellChance) {
                unpriced.add(copy);
                continue;
            }

            // ② 가격 확인
            double price = sellCalculator.calculateTotalPrice(copy, sellLevel);
            if (price <= 0D) {
                unpriced.add(copy);
                continue;
            }

            total += price;
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(copy.getType().name()).append(" x").append(copy.getAmount());
            toSell.add(copy);
        }

        if (total <= 0D) {
            // 판매 대상 없음 — 모두 인벤토리로
            List<ItemStack> all = new ArrayList<>(toSell);
            all.addAll(unpriced);
            return new PricedAutoSellResult(0D, all);
        }

        if (!economy.deposit(player, total)) {
            // 입금 실패 — 모두 인벤토리로
            List<ItemStack> all = new ArrayList<>(toSell);
            all.addAll(unpriced);
            return new PricedAutoSellResult(0D, all);
        }

        // 입금 성공 — 색상 코드 먼저 변환한 뒤 placeholder 치환
        if (config.isAutoSellNotifyPlayer()) {
            String message = config.getAutoSellMessage().replace('&', '§');
            message = message
                    .replace("{summary}", summary.toString())
                    .replace("{total_price}", sellCalculator.formatPrice(total));
            player.sendMessage(message);
        }

        return new PricedAutoSellResult(total, unpriced);
    }
}

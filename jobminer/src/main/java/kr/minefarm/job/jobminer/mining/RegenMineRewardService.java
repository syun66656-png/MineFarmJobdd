package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.autosell.AutoSellProcessor;
import kr.minefarm.job.jobminer.autosell.PricedAutoSellResult;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;

/**
 * 리젠 광산 블록 채굴 보상: {@link MineDropResolver} → 자동판매(가능 시) → 인벤/바닥 드롭 → {@link RegenRestoreService}.
 */
public final class RegenMineRewardService {

    private final MineDropResolver dropResolver;
    private final AutoSellProcessor autoSellProcessor;
    private final RegenRestoreService regenRestoreService;

    public RegenMineRewardService(
            MineDropResolver dropResolver,
            AutoSellProcessor autoSellProcessor,
            RegenRestoreService regenRestoreService
    ) {
        this.dropResolver = dropResolver;
        this.autoSellProcessor = autoSellProcessor;
        this.regenRestoreService = regenRestoreService;
    }

    /**
     * 바닐라 드롭 없이 config 기반 드롭만 처리한 뒤 리젠을 예약한다.
     * <ol>
     *     <li>{@link MineDropResolver#resolveMiningDrops()} 로 guaranteed / special 드롭 목록 생성</li>
     *     <li>자동판매 OFF: 전량 인벤 지급(꽉 차면 발밑 드롭)</li>
     *     <li>자동판매 ON: {@link MineSellCalculator}로 가격이 있는 스택만 합산해 Vault 입금, 가격 없음·입금 실패 시 전량 인벤</li>
     *     <li>항상 {@link RegenRestoreService#scheduleRestore(Block, RegenBlockEntry)} 호출</li>
     * </ol>
     */
    public void deliverRewards(Player player, PlayerJobProfile profile, Block block, RegenBlockEntry entry) {
        List<ItemStack> drops = dropResolver.resolveMiningDrops();

        if (!autoSellProcessor.shouldAttemptAutoSell(profile)) {
            giveItems(player, drops);
            regenRestoreService.scheduleRestore(block, entry);
            return;
        }

        PricedAutoSellResult sellResult = autoSellProcessor.sellPricedStacks(player, profile, drops);
        giveItems(player, sellResult.stacksForInventory());
        regenRestoreService.scheduleRestore(block, entry);
    }

    private void giveItems(Player player, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            ItemStack give = drop.clone();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(give);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
        }
    }
}

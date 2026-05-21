package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.autosell.AutoSellProcessor;
import kr.minefarm.job.jobminer.autosell.PricedAutoSellResult;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.relic.RelicStatService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 리젠 광산 블록 채굴 보상 처리.
 * <ol>
 *   <li>MineDropResolver 로 guaranteed/special 드롭 생성</li>
 *   <li>RELIC 스탯 보너스 드롭 추가 (RelicStatService)</li>
 *   <li>자동판매 처리 (AutoSellProcessor)</li>
 *   <li>인벤토리 지급 (꽉 차면 발밑 드롭)</li>
 *   <li>RELIC 경험치 보너스 적용 후 경험치 지급</li>
 *   <li>리젠 복구 예약 (RegenRestoreService)</li>
 * </ol>
 */
public final class RegenMineRewardService {

    private final MineDropResolver dropResolver;
    private final AutoSellProcessor autoSellProcessor;
    private final RegenRestoreService regenRestoreService;
    private final JobCoreAPI core;
    private final JobMinerConfig config;
    private final RelicStatService relicStatService;

    public RegenMineRewardService(
            MineDropResolver dropResolver,
            AutoSellProcessor autoSellProcessor,
            RegenRestoreService regenRestoreService,
            JobCoreAPI core,
            JobMinerConfig config
    ) {
        this.dropResolver = dropResolver;
        this.autoSellProcessor = autoSellProcessor;
        this.regenRestoreService = regenRestoreService;
        this.core = core;
        this.config = config;
        this.relicStatService = new RelicStatService(config);
    }

    public void deliverRewards(Player player, PlayerJobProfile profile, Block block, RegenBlockEntry entry) {
        // ① 기본 드롭 생성
        List<ItemStack> drops = dropResolver.resolveMiningDrops();

        // ② RELIC 보너스 드롭 — guaranteed 드롭에서만 추가 발동
        List<ItemStack> guaranteedOnly = dropResolver.resolveGuaranteedDropsOnly();
        List<ItemStack> relicBonus = relicStatService.rollBonusDrops(profile, guaranteedOnly);
        if (!relicBonus.isEmpty()) {
            List<ItemStack> combined = new ArrayList<>(drops);
            combined.addAll(relicBonus);
            drops = combined;
        }

        // ③ 자동판매 처리
        if (!autoSellProcessor.shouldAttemptAutoSell(profile)) {
            giveItems(player, drops);
        } else {
            PricedAutoSellResult sellResult = autoSellProcessor.sellPricedStacks(player, profile, drops);
            giveItems(player, sellResult.stacksForInventory());
        }

        // ④ 광물별 경험치 + RELIC 경험치 보너스 지급
        grantMineExp(player, block, profile);

        // ⑤ 리젠 예약
        regenRestoreService.scheduleRestore(block, entry);
    }

    private void grantMineExp(Player player, Block block, PlayerJobProfile profile) {
        MineExpTable expTable = config.getMineExpTable();
        long baseExp = expTable.roll(block.getType());
        if (baseExp <= 0) return;

        // RELIC 스탯 경험치 보너스 적용
        long finalExp = relicStatService.applyExpBonus(baseExp, profile);
        core.getJobExperience().grantExperience(player, finalExp);
    }

    private void giveItems(Player player, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(drop.clone());
            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
        }
    }
}

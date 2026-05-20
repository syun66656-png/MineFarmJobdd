package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.mining.RegenMineRewardService;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * 리젠 광산 채굴: 자동판매·커스텀 드롭·리젠 복구.
 */
public final class MiningListener implements Listener {

    private final JobCoreAPI core;
    private final RegenBlockRegistry regenBlockRegistry;
    private final PickaxeValidator pickaxeValidator;
    private final RegenMineRewardService rewardService;

    public MiningListener(
            JobCoreAPI core,
            RegenBlockRegistry regenBlockRegistry,
            PickaxeValidator pickaxeValidator,
            RegenMineRewardService rewardService
    ) {
        this.core = core;
        this.regenBlockRegistry = regenBlockRegistry;
        this.pickaxeValidator = pickaxeValidator;
        this.rewardService = rewardService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) {
            return;
        }

        if (!pickaxeValidator.isValidPickaxe(player)) {
            event.setCancelled(true);
            player.sendMessage("§c[광산] §f허용된 광부 곡괭이로만 채굴할 수 있습니다.");
            return;
        }

        RegenBlockEntry entry = regenBlockRegistry.getEntry(block);
        if (entry == null) {
            return;
        }

        event.setDropItems(false);
        rewardService.deliverRewards(player, profile, block, entry);
    }
}

package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 리젠 블록 좌표 보호 (일반 유저 파괴·설치 차단).
 */
public final class RegenProtectionListener implements Listener {

    private final RegenBlockRegistry regenBlockRegistry;
    private final JobCoreAPI core;
    private final PickaxeValidator pickaxeValidator;

    public RegenProtectionListener(
            RegenBlockRegistry regenBlockRegistry,
            JobCoreAPI core,
            PickaxeValidator pickaxeValidator
    ) {
        this.regenBlockRegistry = regenBlockRegistry;
        this.core = core;
        this.pickaxeValidator = pickaxeValidator;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) {
            return;
        }
        if (canMineRegenBlock(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c[광산] §f리젠 블록 위치에는 블록을 설치할 수 없습니다.");
    }

    private boolean canMineRegenBlock(Player player) {
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) {
            return false;
        }
        return pickaxeValidator.isValidPickaxe(player);
    }
}

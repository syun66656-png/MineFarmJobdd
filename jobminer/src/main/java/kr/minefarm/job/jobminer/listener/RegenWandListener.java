package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.tool.RegenWandService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 완드 좌클릭 등록·우클릭 해제.
 */
public final class RegenWandListener implements Listener {

    private final RegenWandService wandService;
    private final RegenBlockRegistry regenBlockRegistry;

    public RegenWandListener(RegenWandService wandService, RegenBlockRegistry regenBlockRegistry) {
        this.wandService = wandService;
        this.regenBlockRegistry = regenBlockRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!wandService.isRegenWand(hand)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        event.setCancelled(true);

        if (action == Action.LEFT_CLICK_BLOCK) {
            if (regenBlockRegistry.isRegenBlock(block)) {
                RegenBlockEntry entry = regenBlockRegistry.register(block);
                player.sendMessage("§e[광산] §f리젠 블록 정보를 갱신했습니다. §7("
                        + entry.getMaterial().name() + " @ "
                        + entry.getX() + ", " + entry.getY() + ", " + entry.getZ() + ")");
            } else {
                RegenBlockEntry entry = regenBlockRegistry.register(block);
                player.sendMessage("§a[광산] §f리젠 블록을 등록했습니다. §7("
                        + entry.getMaterial().name() + " @ "
                        + entry.getX() + ", " + entry.getY() + ", " + entry.getZ() + ")");
            }
            return;
        }

        if (regenBlockRegistry.unregister(block)) {
            player.sendMessage("§c[광산] §f리젠 블록 등록을 해제했습니다.");
        } else {
            player.sendMessage("§7[광산] §f등록되지 않은 블록입니다.");
        }
    }
}

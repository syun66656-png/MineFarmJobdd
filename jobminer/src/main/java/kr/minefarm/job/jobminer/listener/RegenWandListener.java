package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
import kr.minefarm.job.jobminer.message.MinerMessages;
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
    private final JobMinerConfig config;
    private final WorldGuardBridge worldGuard;
    private final MinerMessages messages;

    public RegenWandListener(
            RegenWandService wandService,
            RegenBlockRegistry regenBlockRegistry,
            JobMinerConfig config,
            WorldGuardBridge worldGuard,
            MinerMessages messages
    ) {
        this.wandService = wandService;
        this.regenBlockRegistry = regenBlockRegistry;
        this.config = config;
        this.worldGuard = worldGuard;
        this.messages = messages;
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

        // ★ WorldGuard 리전 검사 — allowed-regions가 설정되어 있으면 그 안에서만 등록 가능
        if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) {
            player.sendMessage(messages.format("wand-not-in-region"));
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            if (regenBlockRegistry.isRegenBlock(block)) {
                RegenBlockEntry entry = regenBlockRegistry.register(block);
                player.sendMessage(messages.format("wand-updated", java.util.Map.of(
                        "material", entry.getMaterial().name(),
                        "x", String.valueOf(entry.getX()),
                        "y", String.valueOf(entry.getY()),
                        "z", String.valueOf(entry.getZ()))));
            } else {
                RegenBlockEntry entry = regenBlockRegistry.register(block);
                player.sendMessage(messages.format("wand-registered", java.util.Map.of(
                        "material", entry.getMaterial().name(),
                        "x", String.valueOf(entry.getX()),
                        "y", String.valueOf(entry.getY()),
                        "z", String.valueOf(entry.getZ()))));
            }
            return;
        }

        if (regenBlockRegistry.unregister(block)) {
            player.sendMessage(messages.format("wand-unregistered"));
        } else {
            player.sendMessage(messages.format("wand-not-found"));
        }
    }
}

package kr.minefarm.job.jobcore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 열린 GUI 세션 추적 및 클릭 위임.
 */
public final class GuiManager {

    private final Map<UUID, JobGui> openGuis = new ConcurrentHashMap<>();

    public void track(Player player, JobGui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    public void untrack(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof JobGui gui)) {
            return;
        }
        JobGui tracked = openGuis.get(player.getUniqueId());
        if (tracked != gui) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(gui.getInventory())) {
            return;
        }
        gui.handleClick(event);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof JobGui) {
            openGuis.remove(player.getUniqueId());
        }
    }
}

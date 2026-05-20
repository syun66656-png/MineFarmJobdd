package kr.minefarm.job.jobcore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * {@link InventoryHolder} 기반 JobCore GUI 계약.
 */
public interface JobGui extends InventoryHolder {

    GuiType getType();

    void open(Player player);

    void handleClick(InventoryClickEvent event);

    @Override
    Inventory getInventory();
}

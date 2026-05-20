package kr.minefarm.job.jobcore.gui;

import kr.minefarm.job.jobcore.config.GuiConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

abstract class AbstractJobGui implements JobGui {

    protected final JavaPlugin plugin;
    protected final GuiConfig guiConfig;
    protected final Inventory inventory;

    protected AbstractJobGui(JavaPlugin plugin, GuiConfig guiConfig, String configKey) {
        this(plugin, guiConfig, configKey, null);
    }

    protected AbstractJobGui(JavaPlugin plugin, GuiConfig guiConfig, String configKey, String titleOverride) {
        this.plugin = plugin;
        this.guiConfig = guiConfig;
        String title = titleOverride != null ? titleOverride : guiConfig.getTitle(configKey);
        this.inventory = Bukkit.createInventory(this, guiConfig.getSize(configKey), title);
        fillBackground(configKey);
    }

    private void fillBackground(String configKey) {
        guiConfig.getFiller(configKey).ifPresent(template -> {
            ItemStack filler = createItem(template.material(), template.name(), template.lore());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        });
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void setTemplateItem(int slot, Material material, String name, List<String> lore) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, createItem(material, name, lore));
    }

    protected static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

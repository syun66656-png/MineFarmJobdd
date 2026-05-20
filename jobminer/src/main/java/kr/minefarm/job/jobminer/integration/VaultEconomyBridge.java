package kr.minefarm.job.jobminer.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Vault Economy soft dependency.
 */
public final class VaultEconomyBridge {

    private final JavaPlugin plugin;
    private Object economy;
    private boolean available;

    public VaultEconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            available = false;
            return;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServer().getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                available = false;
                return;
            }
            economy = registration.getProvider();
            available = economy != null;
            if (available) {
                plugin.getLogger().info("[JobMiner] Vault Economy detected.");
            }
        } catch (ClassNotFoundException exception) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean deposit(Player player, double amount) {
        if (!available || amount <= 0D) {
            return false;
        }
        try {
            Method deposit = economy.getClass().getMethod("depositPlayer", Player.class, double.class);
            Object response = deposit.invoke(economy, player, amount);
            if (response == null) {
                return false;
            }
            Method succeeded = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(succeeded.invoke(response));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "[JobMiner] Vault deposit failed", exception);
            return false;
        }
    }
}

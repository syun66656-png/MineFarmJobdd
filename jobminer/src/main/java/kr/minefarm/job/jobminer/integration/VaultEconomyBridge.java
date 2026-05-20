package kr.minefarm.job.jobminer.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Vault Economy soft dependency.
 * <p>
 * depositPlayer 는 {@code (OfflinePlayer, double)} 시그니처를 사용해야 한다.
 * Method 객체는 {@link #detect()} 시점에 한 번만 룩업하여 캐싱한다.
 */
public final class VaultEconomyBridge {

    private final JavaPlugin plugin;
    private Object economy;
    private boolean available;

    /** 캐싱된 depositPlayer(OfflinePlayer, double) 메서드 */
    private Method depositMethod;
    /** 캐싱된 EconomyResponse#transactionSuccess() 메서드 */
    private Method transactionSuccessMethod;

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
            if (economy == null) {
                available = false;
                return;
            }

            // OfflinePlayer 시그니처로 캐싱 (Player.class 는 NoSuchMethodException 발생)
            depositMethod = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);

            // EconomyResponse 클래스의 transactionSuccess 도 캐싱
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            transactionSuccessMethod = responseClass.getMethod("transactionSuccess");

            available = true;
            plugin.getLogger().info("[JobMiner] Vault Economy detected.");

        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            available = false;
            plugin.getLogger().warning("[JobMiner] Vault Economy not available: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * {@code depositPlayer(OfflinePlayer, double)} 로 입금한다.
     *
     * @param player 입금 대상
     * @param amount 금액 (0 이하이면 즉시 false)
     * @return 입금 성공 여부
     */
    public boolean deposit(Player player, double amount) {
        if (!available || amount <= 0D || depositMethod == null || transactionSuccessMethod == null) {
            return false;
        }
        try {
            Object response = depositMethod.invoke(economy, (OfflinePlayer) player, amount);
            if (response == null) {
                return false;
            }
            return Boolean.TRUE.equals(transactionSuccessMethod.invoke(response));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "[JobMiner] Vault deposit failed", exception);
            return false;
        }
    }
}

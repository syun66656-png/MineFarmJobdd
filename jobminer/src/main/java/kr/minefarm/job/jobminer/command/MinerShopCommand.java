package kr.minefarm.job.jobminer.command;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.message.MinerMessages;
import kr.minefarm.job.jobminer.shop.MinerShopGui;
import kr.minefarm.job.jobminer.shop.MinerShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /광부상점 — 광부 전용 판매 상점 GUI를 연다.
 * <p>
 * 광부 직업이 없으면 거부한다.
 * Vault 미설치 시 경고 후 거부한다.
 */
public final class MinerShopCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JobCoreAPI core;
    private final JobMinerConfig config;
    private final MinerShopService shopService;
    private final MinerMessages messages;

    public MinerShopCommand(
            JavaPlugin plugin,
            JobCoreAPI core,
            JobMinerConfig config,
            MinerShopService shopService,
            MinerMessages messages
    ) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.shopService = shopService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("shop-player-only"));
            return true;
        }

        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(messages.format("shop-profile-loading"));
            return true;
        }

        if (profile.getJobId() != JobId.MINER) {
            player.sendMessage(messages.format("shop-not-miner"));
            return true;
        }

        // Vault 확인
        if (!shopService.isEconomyAvailable()) {
            player.sendMessage(messages.format("shop-no-vault"));
            return true;
        }

        new MinerShopGui(plugin, core, config, shopService, messages, player).open();
        return true;
    }
}

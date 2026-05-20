package kr.minefarm.job.jobminer.command;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
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

    public MinerShopCommand(
            JavaPlugin plugin,
            JobCoreAPI core,
            JobMinerConfig config,
            MinerShopService shopService
    ) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.shopService = shopService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }

        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null) {
            player.sendMessage("§7프로필을 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
            return true;
        }

        if (profile.getJobId() != JobId.MINER) {
            player.sendMessage("§c광부 직업만 상점을 이용할 수 있습니다.");
            return true;
        }

        // Vault 확인
        if (!shopService.isEconomyAvailable()) {
            player.sendMessage("§c경제 플러그인(Vault)이 설치되어 있지 않아 상점을 이용할 수 없습니다.");
            return true;
        }

        new MinerShopGui(plugin, core, config, shopService, player).open();
        return true;
    }
}

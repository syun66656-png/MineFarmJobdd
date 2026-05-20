package kr.minefarm.job.jobminer.command;

import kr.minefarm.job.jobminer.tool.RegenWandService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegenWandCommand implements CommandExecutor {

    private final RegenWandService wandService;

    public RegenWandCommand(RegenWandService wandService) {
        this.wandService = wandService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        ItemStack wand = wandService.createWand();
        player.getInventory().addItem(wand);
        player.sendMessage("§a[광산] §f관리 완드를 지급했습니다. §7(좌클릭: 등록, 우클릭: 해제)");
        return true;
    }
}

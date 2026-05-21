package kr.minefarm.job.jobminer.command;

import kr.minefarm.job.jobminer.tool.RegenWandService;
import kr.minefarm.job.jobminer.message.MinerMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegenWandCommand implements CommandExecutor {

    private final RegenWandService wandService;
    private final MinerMessages messages;

    public RegenWandCommand(RegenWandService wandService, MinerMessages messages) {
        this.wandService = wandService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("player-only"));
            return true;
        }
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage(messages.format("no-permission"));
            return true;
        }

        ItemStack wand = wandService.createWand();
        player.getInventory().addItem(wand);
        player.sendMessage(messages.format("wand-given"));
        return true;
    }
}

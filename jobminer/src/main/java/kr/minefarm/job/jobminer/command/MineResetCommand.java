package kr.minefarm.job.jobminer.command;

import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.mining.RegenRestoreService;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class MineResetCommand implements CommandExecutor, TabCompleter {

    private final RegenBlockRegistry regenBlockRegistry;
    private final RegenRestoreService regenRestoreService;
    private final MinerMessages messages;

    public MineResetCommand(RegenBlockRegistry regenBlockRegistry, RegenRestoreService regenRestoreService, MinerMessages messages) {
        this.regenBlockRegistry = regenBlockRegistry;
        this.regenRestoreService = regenRestoreService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage(messages.format("no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            int count = regenRestoreService.resetAll();
            sender.sendMessage(messages.format("mine-reset-all", java.util.Map.of("count", String.valueOf(count))));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("mine-reset-player-only"));
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(messages.format("mine-reset-no-target"));
            return true;
        }

        RegenBlockEntry entry = regenBlockRegistry.getEntry(target);
        if (entry == null) {
            player.sendMessage(messages.format("mine-reset-not-regen"));
            return true;
        }

        if (regenRestoreService.resetNow(entry)) {
            player.sendMessage(messages.format("mine-reset-success", java.util.Map.of("material", entry.getMaterial().name())));
        } else {
            player.sendMessage(messages.format("mine-reset-no-world"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("all");
        }
        return List.of();
    }
}

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

    public MineResetCommand(RegenBlockRegistry regenBlockRegistry, RegenRestoreService regenRestoreService) {
        this.regenBlockRegistry = regenBlockRegistry;
        this.regenRestoreService = regenRestoreService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            int count = regenRestoreService.resetAll();
            sender.sendMessage("§a[광산] §f전체 리젠 블록 " + count + "개를 복구했습니다.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 블록 단위 초기화를 사용할 수 있습니다. 전체는 §f/광산초기화 all");
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage("§c[광산] §f바라보는 블록이 없습니다.");
            return true;
        }

        RegenBlockEntry entry = regenBlockRegistry.getEntry(target);
        if (entry == null) {
            player.sendMessage("§c[광산] §f리젠 블록이 아닙니다.");
            return true;
        }

        if (regenRestoreService.resetNow(entry)) {
            player.sendMessage("§a[광산] §f블록을 즉시 복구했습니다. §7(" + entry.getMaterial().name() + ")");
        } else {
            player.sendMessage("§c[광산] §f월드를 찾을 수 없어 복구하지 못했습니다.");
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

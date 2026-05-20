package kr.minefarm.job.jobcore.command.admin;

import kr.minefarm.job.jobcore.bootstrap.JobCoreBootstrap;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.registry.JobModuleLoader;
import kr.minefarm.job.jobcore.service.JobGuiService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobAdminCommand implements CommandExecutor, TabCompleter {

    private final JobGuiService guiService;
    private final MessageConfig messages;
    private final JobCoreBootstrap bootstrap;

    public JobAdminCommand(JobGuiService guiService, MessageConfig messages, JobCoreBootstrap bootstrap) {
        this.guiService = guiService;
        this.messages = messages;
        this.bootstrap = bootstrap;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minefarmjob.admin")) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }

        if ("직업리로드".equalsIgnoreCase(command.getName())) {
            return handleReload(sender, args);
        }

        if (!(sender instanceof Player admin)) {
            sender.sendMessage(messages.get("player-only"));
            return true;
        }
        if (args.length < 1) {
            admin.sendMessage("§c사용법: /" + label + " <닉네임>");
            return true;
        }

        var target = Bukkit.getOfflinePlayer(args[0]);
        guiService.openAdminGuiAsync(admin, target);
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c사용법: /직업리로드 <core|all|모듈ID>");
            sender.sendMessage("§7예: §f/직업리로드 jobminer §7| §f/직업리로드 core §7| §f/직업리로드 all");
            return true;
        }

        String target = args[0].trim().toLowerCase(Locale.ROOT);
        if ("all".equals(target)) {
            bootstrap.reloadAll(sender);
            return true;
        }

        var moduleId = bootstrap.getModuleLoader().findModuleId(target);
        if (moduleId.isEmpty()) {
            sender.sendMessage("§c[JobCore] §f알 수 없는 대상: §e" + args[0]);
            sender.sendMessage("§7사용 가능: §fcore§7, §fall§7, §f"
                    + String.join("§7, §f", bootstrap.getModuleLoader().getModuleIds()));
            return true;
        }

        if ("all".equals(moduleId.get())) {
            bootstrap.reloadAll(sender);
        } else {
            bootstrap.reloadTarget(sender, moduleId.get());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!"직업리로드".equalsIgnoreCase(command.getName())) {
            return List.of();
        }
        if (!sender.hasPermission("minefarmjob.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add(JobModuleLoader.CORE_ID);
            suggestions.add("all");
            suggestions.addAll(bootstrap.getModuleLoader().getModuleIds());
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}

package kr.minefarm.job.jobcore.command;

import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.service.JobGuiService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JobCommand implements CommandExecutor {

    private final JobGuiService guiService;
    private final MessageConfig messages;

    public JobCommand(JobGuiService guiService, MessageConfig messages) {
        this.guiService = guiService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("player-only"));
            return true;
        }
        player.sendMessage(messages.get("profile-loading"));
        guiService.openMainMenuAsync(player);
        return true;
    }
}

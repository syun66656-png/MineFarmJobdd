package kr.minefarm.job.jobcore.gui;

import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.JobGuiService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 직업 선택 GUI (직업 없을 때 / 관리자 연동용).
 */
public final class JobSelectGui extends AbstractJobGui {

    private final JobGuiService guiService;
    private final MessageConfig messages;
    private final JobRegistry jobRegistry;
    private final Map<Integer, JobId> slotJobs = new HashMap<>();

    public JobSelectGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            JobGuiService guiService,
            MessageConfig messages,
            JobRegistry jobRegistry
    ) {
        super(plugin, guiConfig, "job-select");
        this.guiService = guiService;
        this.messages = messages;
        this.jobRegistry = jobRegistry;
        buildJobItems();
    }

    private void buildJobItems() {
        for (Map.Entry<String, GuiConfig.GuiItemTemplate> entry : guiConfig.getJobSelectEntries().entrySet()) {
            JobId jobId = JobId.fromKey(entry.getKey()).orElse(null);
            if (jobId == null || !jobId.hasJob()) {
                continue;
            }
            GuiConfig.GuiItemTemplate template = entry.getValue();
            slotJobs.put(template.slot(), jobId);
            setTemplateItem(
                    template.slot(),
                    template.material(),
                    template.name(),
                    template.lore()
            );
        }
    }

    @Override
    public GuiType getType() {
        return GuiType.JOB_SELECT;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        JobId jobId = slotJobs.get(event.getSlot());
        if (jobId == null) {
            return;
        }
        if (!jobRegistry.isRegistered(jobId)) {
            player.sendMessage(messages.get("job-not-registered"));
            return;
        }
        guiService.selectJobAsync(player, jobId, null);
    }
}

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
import java.util.UUID;

/**
 * 직업 선택 GUI.
 * <ul>
 *   <li>일반 모드: 플레이어 본인 직업 변경</li>
 *   <li>관리자 모드: targetUuid 지정 시 관리자 화면에서 대상 직업 변경 (오프라인 포함)</li>
 * </ul>
 */
public final class JobSelectGui extends AbstractJobGui {

    private final JobGuiService guiService;
    private final MessageConfig messages;
    private final JobRegistry jobRegistry;
    private final Map<Integer, JobId> slotJobs = new HashMap<>();

    /** 관리자 모드일 때 대상 UUID; null이면 일반 모드 */
    private final UUID targetUuid;
    /** 관리자 모드일 때 대상 이름 (메시지용); null이면 일반 모드 */
    private final String targetName;

    /** 일반 모드 생성자 */
    public JobSelectGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            JobGuiService guiService,
            MessageConfig messages,
            JobRegistry jobRegistry
    ) {
        this(plugin, guiConfig, guiService, messages, jobRegistry, null, null);
    }

    /** 관리자 모드 생성자 */
    public JobSelectGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            JobGuiService guiService,
            MessageConfig messages,
            JobRegistry jobRegistry,
            UUID targetUuid,
            String targetName
    ) {
        super(plugin, guiConfig, "job-select",
                resolveTitle(guiConfig, targetName));
        this.guiService = guiService;
        this.messages = messages;
        this.jobRegistry = jobRegistry;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        buildJobItems();
    }

    private static String resolveTitle(GuiConfig guiConfig, String targetName) {
        String base = guiConfig.getTitle("job-select");
        if (targetName == null) return base;
        return "§c[관리] " + base + " §7→ §f" + targetName;
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
        if (targetUuid != null) {
            // 관리자 모드 — 대상 UUID로 직업 변경
            guiService.selectJobForTargetAsync(player, targetUuid, targetName, jobId);
        } else {
            // 일반 모드 — 본인 직업 변경
            guiService.selectJobAsync(player, jobId, null);
        }
    }
}

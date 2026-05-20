package kr.minefarm.job.jobcore.gui;

import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.service.RankingService;
import kr.minefarm.job.jobcore.util.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * 직업 정보 센터 GUI — {@link RankingService} 캐시 기준 직업별 순위 표시.
 */
public final class JobInfoGui extends AbstractJobGui {

    private final PlaceholderResolver placeholderResolver;
    private final RankingService rankingService;
    private final Player viewer;
    private final PlayerJobProfile profile;

    public JobInfoGui(
            JavaPlugin plugin,
            GuiConfig guiConfig,
            PlaceholderResolver placeholderResolver,
            RankingService rankingService,
            Player viewer,
            PlayerJobProfile profile,
            int rankAtOpen
    ) {
        super(plugin, guiConfig, "job-info");
        this.placeholderResolver = placeholderResolver;
        this.rankingService = rankingService;
        this.viewer = viewer;
        this.profile = profile;
        buildContent(resolveRank(rankAtOpen));
    }

    /** 랭킹 갱신 후 열린 GUI 로어를 최신 순위로 다시 그린다. */
    public void refreshRankingDisplay() {
        buildContent(resolveRank(-1));
    }

    private int resolveRank(int rankAtOpen) {
        if (rankingService.hasCache()) {
            return rankingService.getRank(viewer.getUniqueId(), profile.getJobId());
        }
        return rankAtOpen;
    }

    private void buildContent(int rank) {
        Map<String, String> keys = Map.of(
                "current-job", "current-job",
                "experience", "experience",
                "ranking", "ranking"
        );
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            guiConfig.getItem("job-info", entry.getValue()).ifPresent(template -> {
                String name = placeholderResolver.resolve(viewer, template.name(), profile, rank);
                var lore = placeholderResolver.resolveLore(viewer, template.lore(), profile, rank);
                setTemplateItem(template.slot(), template.material(), name, lore);
            });
        }
    }

    @Override
    public GuiType getType() {
        return GuiType.JOB_INFO;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // 정보 전용
    }
}

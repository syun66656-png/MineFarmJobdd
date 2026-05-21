package kr.minefarm.job.jobminer.relic;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 유물(RELIC) 스탯 효과.
 * <p>
 * <b>효과 1 — 보너스 드롭</b>: 채굴 시 RELIC 레벨 × bonus-drop-chance-per-level 확률로
 * guaranteed-drops 를 한 번 더 지급한다.
 * <p>
 * <b>효과 2 — 경험치 증폭</b>: 기본 채굴 경험치에 RELIC 레벨 × exp-bonus-per-level 비율을 더한다.
 * 예) RELIC 10, exp-bonus-per-level 0.02 → 경험치 +20%
 */
public final class RelicStatService {

    private final JobMinerConfig config;

    public RelicStatService(JobMinerConfig config) {
        this.config = config;
    }

    /**
     * RELIC 스탯 보너스 드롭을 계산한다.
     * 확률 판정 성공 시 guaranteedDrops 목록을 복제해 추가 반환.
     *
     * @param profile        플레이어 프로필
     * @param guaranteedDrops 기본 100% 드롭 목록
     * @return 추가 지급할 아이템 목록 (없으면 빈 리스트)
     */
    public List<ItemStack> rollBonusDrops(PlayerJobProfile profile, List<ItemStack> guaranteedDrops) {
        int relicLevel = profile.getStatLevel(StatType.RELIC);
        if (relicLevel <= 0) return List.of();

        double chancePerLevel = getBonusDropChancePerLevel();
        double totalChance = chancePerLevel * relicLevel;
        if (totalChance <= 0) return List.of();

        if (ThreadLocalRandom.current().nextDouble() >= totalChance) return List.of();

        // 추가 드롭 발동: guaranteedDrops 를 bonus-drop-multiplier 배수로 복사
        double multiplier = Math.max(1.0, getBonusDropMultiplier());
        List<ItemStack> bonus = new ArrayList<>();
        for (ItemStack stack : guaranteedDrops) {
            if (stack == null || stack.getType().isAir()) continue;
            int bonusAmount = (int) Math.max(1, Math.round(stack.getAmount() * multiplier));
            ItemStack bonusStack = stack.clone();
            bonusStack.setAmount(bonusAmount);
            bonus.add(bonusStack);
        }
        return bonus;
    }

    /**
     * 기본 경험치에 RELIC 보너스를 적용해 최종 경험치를 반환한다.
     *
     * @param baseExp   MineExpTable에서 roll된 기본 경험치
     * @param profile   플레이어 프로필
     * @return 보너스 적용된 최종 경험치
     */
    public long applyExpBonus(long baseExp, PlayerJobProfile profile) {
        if (baseExp <= 0) return baseExp;
        int relicLevel = profile.getStatLevel(StatType.RELIC);
        if (relicLevel <= 0) return baseExp;

        double bonusRate = getExpBonusPerLevel() * relicLevel;
        return Math.max(baseExp, Math.round(baseExp * (1.0 + bonusRate)));
    }

    // ── config 읽기 ──────────────────────────────────────────────────────────

    private ConfigurationSection getRelicSection() {
        return config.getRawConfig().getConfigurationSection("relic");
    }

    private double getBonusDropChancePerLevel() {
        ConfigurationSection s = getRelicSection();
        return s != null ? s.getDouble("bonus-drop-chance-per-level", 0.02) : 0.02;
    }

    private double getBonusDropMultiplier() {
        ConfigurationSection s = getRelicSection();
        return s != null ? s.getDouble("bonus-drop-multiplier", 1.0) : 1.0;
    }

    private double getExpBonusPerLevel() {
        ConfigurationSection s = getRelicSection();
        return s != null ? s.getDouble("exp-bonus-per-level", 0.02) : 0.02;
    }
}

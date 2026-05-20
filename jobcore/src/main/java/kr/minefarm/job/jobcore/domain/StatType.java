package kr.minefarm.job.jobcore.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 투자 가능한 4종 스탯.
 */
public enum StatType {

    RELIC("relic", "유물"),
    SKILL("skill", "스킬"),
    SELL("sell", "판매"),
    AUTO_SELL("auto_sell", "자동판매");

    private final String key;
    private final String displayName;

    StatType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** PAPI 접미사: %jobcore_stat_relic% 등 */
    public String getPlaceholderSuffix() {
        return key;
    }

    public static Optional<StatType> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (StatType type : values()) {
            if (type.key.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

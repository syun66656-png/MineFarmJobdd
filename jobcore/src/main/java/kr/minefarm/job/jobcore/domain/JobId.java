package kr.minefarm.job.jobcore.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 서버 전역 직업 식별자.
 * 모듈별 접두사 없이 단일 enum으로 관리해 DB·PAPI와 일치시킨다.
 */
public enum JobId {

    NONE("none", "없음"),
    MINER("miner", "광부"),
    FARMER("farmer", "농부"),
    HUNTER("hunter", "헌터");

    private final String key;
    private final String displayName;

    JobId(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasJob() {
        return this != NONE;
    }

    public static Optional<JobId> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.of(NONE);
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (JobId id : values()) {
            if (id.key.equals(normalized)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}

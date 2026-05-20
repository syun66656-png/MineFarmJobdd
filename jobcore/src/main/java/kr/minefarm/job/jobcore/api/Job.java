package kr.minefarm.job.jobcore.api;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import org.bukkit.entity.Player;

/**
 * 직업 타입 하나를 나타내는 계약.
 * 각 직업 모듈(jobminer, jobfarmer, …)이 구현체를 제공한다.
 */
public interface Job {

    JobId getId();

    String getDisplayName();

    /** 직업 선택 시 호출 (모듈별 초기화·안내 메시지 등). */
    default void onSelect(Player player) {
    }

    /** 다른 직업으로 변경될 때 호출 (버프 해제 등). */
    default void onDeselect(Player player) {
    }

    /**
     * DB에서 프로필이 로드된 뒤 또는 직업/스탯 변경 직후, 메인 스레드에서 호출된다.
     * 레벨·스탯 기반 패시브(이속 등)를 적용한다.
     */
    default void refreshPassiveEffects(Player player, PlayerJobProfile profile) {
    }

    default boolean isEnabled() {
        return true;
    }
}

package kr.minefarm.job.jobcore.api;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Velocity 프록시 연동 추상화.
 * {@code velocity-support: false} 이면 no-op 구현이 주입된다.
 */
public interface VelocityProvider {

    /**
     * config.yml 의 velocity-support 값.
     */
    boolean isEnabled();

    /**
     * 프록시가 전달한 플레이어 원본 IP (없으면 empty).
     */
    Optional<String> getForwardedAddress(UUID playerId);

    Optional<String> getForwardedAddress(Player player);

    /**
     * 현재 접속 중인 백엔드(로비·광산 등) 서버 이름.
     */
    Optional<String> getCurrentServer(UUID playerId);

    /**
     * 직전에 있던 백엔드 서버 이름 (서버 이동 직후).
     */
    Optional<String> getPreviousServer(UUID playerId);
}

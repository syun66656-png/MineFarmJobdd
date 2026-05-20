package kr.minefarm.job.jobcore.integration.velocity;

import kr.minefarm.job.jobcore.api.VelocityProvider;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * velocity-support 가 false 일 때 사용. 채널·캐시 없음.
 */
public enum NoOpVelocityProvider implements VelocityProvider {

    INSTANCE;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<String> getForwardedAddress(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getForwardedAddress(Player player) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentServer(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getPreviousServer(UUID playerId) {
        return Optional.empty();
    }
}

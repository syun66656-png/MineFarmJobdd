package kr.minefarm.job.jobcore.integration.velocity;

import java.util.Optional;

final class VelocityPlayerState {

    private String forwardedAddress;
    private String currentServer;
    private String previousServer;

    void setForwardedAddress(String forwardedAddress) {
        this.forwardedAddress = forwardedAddress;
    }

    void switchServer(String newServer) {
        if (newServer == null || newServer.isBlank()) {
            return;
        }
        if (currentServer != null && !currentServer.equals(newServer)) {
            previousServer = currentServer;
        }
        currentServer = newServer;
    }

    void applyServerSwitch(String fromServer, String toServer) {
        if (fromServer != null && !fromServer.isBlank()) {
            previousServer = fromServer;
        }
        if (toServer != null && !toServer.isBlank()) {
            if (currentServer != null && !currentServer.equals(toServer)) {
                previousServer = currentServer;
            }
            currentServer = toServer;
        }
    }

    Optional<String> forwardedAddress() {
        return optional(forwardedAddress);
    }

    Optional<String> currentServer() {
        return optional(currentServer);
    }

    Optional<String> previousServer() {
        return optional(previousServer);
    }

    private static Optional<String> optional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

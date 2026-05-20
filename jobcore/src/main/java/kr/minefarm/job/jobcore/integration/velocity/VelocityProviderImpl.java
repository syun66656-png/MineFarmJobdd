package kr.minefarm.job.jobcore.integration.velocity;

import kr.minefarm.job.jobcore.api.VelocityProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Velocity 프록시(또는 동행 Velocity 플러그인)와 {@value #CHANNEL} 채널로 통신한다.
 *
 * <p>페이로드 (UTF-8 텍스트):
 * <ul>
 *   <li>{@code IP|<uuid>|<address>}</li>
 *   <li>{@code SWITCH|<uuid>|<fromServer>|<toServer>}</li>
 * </ul>
 *
 * <p><b>보안</b>: 클라이언트 모드가 임의 UUID 를 담아 페이로드를 보낼 수 있으므로,
 * 페이로드의 UUID 가 실제 메시지를 보낸 플레이어의 UUID 와 다르면 무시한다.
 */
public final class VelocityProviderImpl implements VelocityProvider, PluginMessageListener, Listener {

    public static final String CHANNEL = "minefarm:jobcore";

    private final JavaPlugin plugin;
    private final Map<UUID, VelocityPlayerState> playerStates = new ConcurrentHashMap<>();
    private boolean started;

    public VelocityProviderImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        started = true;
        plugin.getLogger().info("[JobCore] Velocity support enabled (channel=" + CHANNEL + ").");
    }

    public void shutdown() {
        if (!started) return;
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        HandlerList.unregisterAll(this);
        playerStates.clear();
        started = false;
    }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public Optional<String> getForwardedAddress(UUID playerId) {
        VelocityPlayerState state = playerStates.get(playerId);
        return state != null ? state.forwardedAddress() : Optional.empty();
    }

    @Override
    public Optional<String> getForwardedAddress(Player player) {
        return getForwardedAddress(player.getUniqueId());
    }

    @Override
    public Optional<String> getCurrentServer(UUID playerId) {
        VelocityPlayerState state = playerStates.get(playerId);
        return state != null ? state.currentServer() : Optional.empty();
    }

    @Override
    public Optional<String> getPreviousServer(UUID playerId) {
        VelocityPlayerState state = playerStates.get(playerId);
        return state != null ? state.previousServer() : Optional.empty();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length == 0) return;
        try {
            handlePayload(player.getUniqueId(), new String(message, StandardCharsets.UTF_8).trim());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "[JobCore] Ignored malformed Velocity payload.", exception);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerStates.remove(event.getPlayer().getUniqueId());
    }

    /**
     * @param senderId 실제 메시지를 보낸 플레이어 UUID (위조 방지 검증에 사용)
     */
    void handlePayload(UUID senderId, String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 2) return;

        String type = parts[0].toUpperCase(Locale.ROOT);
        UUID playerId;
        try {
            playerId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            return;
        }

        // 보안: 페이로드 UUID가 실제 송신자와 다르면 무시
        if (!playerId.equals(senderId)) {
            plugin.getLogger().warning("[JobCore] Velocity 페이로드 UUID 불일치 — 무시함."
                    + " sender=" + senderId + " claimed=" + playerId);
            return;
        }

        VelocityPlayerState state = playerStates.computeIfAbsent(playerId, ignored -> new VelocityPlayerState());

        switch (type) {
            case "IP" -> {
                if (parts.length >= 3) state.setForwardedAddress(parts[2]);
            }
            case "SWITCH" -> {
                if (parts.length >= 4) state.applyServerSwitch(parts[2], parts[3]);
                else if (parts.length == 3) state.switchServer(parts[2]);
            }
            default -> { /* 알 수 없는 타입 무시 */ }
        }
    }
}

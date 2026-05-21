package kr.minefarm.job.jobcore.boost;

import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 경험치 부스트 쿠폰 우클릭 사용 처리.
 * 인벤 우클릭은 PlayerInteractEvent + EquipmentSlot.HAND 만 처리.
 */
public final class BoostInteractListener implements Listener {

    private final ExperienceBoostItem boostItem;
    private final PlayerProfileService profileService;
    private final MessageConfig messages;

    public BoostInteractListener(
            ExperienceBoostItem boostItem,
            PlayerProfileService profileService,
            MessageConfig messages
    ) {
        this.boostItem = boostItem;
        this.profileService = profileService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        ExperienceBoostItem.BoostData data = boostItem.read(hand);
        if (data == null) return;

        // 우리 쿠폰 — 인터랙트 cancel 후 진행
        event.setCancelled(true);

        PlayerJobProfile profile = profileService.getCached(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(messages.format("boost-profile-loading", Map.of()));
            profileService.loadOrCreate(player);
            return;
        }

        long now = System.currentTimeMillis();
        // 중복 사용 차단
        if (profile.isBoostActive() && profile.getBoostExpiryTime() > now) {
            player.sendMessage(messages.format("boost-already-active", Map.of()));
            return;
        }

        // 적용
        long durationMs = data.minutes() * 60_000L;
        profile.setBoostMultiplier(data.multiplier());
        profile.setBoostExpiryTime(now + durationMs);

        // 아이템 1개 소모
        hand.setAmount(hand.getAmount() - 1);

        // 즉시 저장 (휘발성 손실 방지)
        profileService.saveAsync(profile);

        player.sendMessage(messages.format("boost-started", Map.of(
                "multiplier", formatMultiplier(data.multiplier()),
                "minutes", String.valueOf(data.minutes())
        )));
    }

    /** 배율 표시: 정수면 '2', 소수면 '1.5' */
    private static String formatMultiplier(double m) {
        if (m == Math.rint(m)) return String.valueOf((long) m);
        return String.format(java.util.Locale.ROOT, "%.1f", m);
    }
}

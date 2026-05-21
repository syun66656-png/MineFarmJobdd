package kr.minefarm.job.jobminer.skill;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.mining.MineOreMaterials;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
import kr.minefarm.job.jobminer.mining.RegenMineRewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 광부 스킬: 다이너마이트(리젠 광석 폭발 + {@link RegenMineRewardService}),
 * 오버클럭(Haste + 액션바·쿨타임).
 */
public final class MinerSkills implements Listener {

    private static final long MS_PER_TICK = 50L;

    private final JavaPlugin plugin;
    private final JobMinerConfig config;
    private final JobCoreAPI core;
    private final RegenBlockRegistry regenBlockRegistry;
    private final RegenMineRewardService rewardService;
    private final WorldGuardBridge worldGuard;
    private final NamespacedKey dynamiteKey;
    private final NamespacedKey dynamiteOwnerKey;
    private final NamespacedKey overclockHasteKey;

    private final Map<UUID, Long> overclockActiveUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> overclockCooldownUntilMs = new ConcurrentHashMap<>();
    private BukkitTask overclockUiTask;

    public MinerSkills(
            JavaPlugin plugin,
            JobMinerConfig config,
            JobCoreAPI core,
            RegenBlockRegistry regenBlockRegistry,
            RegenMineRewardService rewardService,
            WorldGuardBridge worldGuard
    ) {
        this.plugin = plugin;
        this.config = config;
        this.core = core;
        this.regenBlockRegistry = regenBlockRegistry;
        this.rewardService = rewardService;
        this.worldGuard = worldGuard;
        this.dynamiteKey = new NamespacedKey(plugin, "minefarm_dynamite");
        this.dynamiteOwnerKey = new NamespacedKey(plugin, "dynamite_owner");
        this.overclockHasteKey = new NamespacedKey(plugin, "miner_overclock_haste");
        this.overclockUiTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickOverclockActionBar, 4L, 4L);
    }

    /** 직업 해제·모듈 비활성화 시 플레이어 스킬 상태 정리 */
    public void clearForPlayer(Player player) {
        UUID id = player.getUniqueId();
        overclockActiveUntilMs.remove(id);
        overclockCooldownUntilMs.remove(id);
        clearOverclockHaste(player);
        player.sendActionBar(Component.empty());
    }

    public void shutdown() {
        if (overclockUiTask != null) {
            overclockUiTask.cancel();
            overclockUiTask = null;
        }
        overclockActiveUntilMs.clear();
        overclockCooldownUntilMs.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        // PlayerInteractEvent는 MAIN_HAND, OFF_HAND 두 번 발생 — 메인 핸드만 처리
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // useItemInHand() == DENY 인 경우에도 우리 스킬은 발동해야 함 (보호 플러그인이 막아도 우클릭은 처리)
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // 스킬 아이템(다이너마이트/오버클럭)을 들고 우클릭했는지 먼저 확인
        boolean holdingSkillItem =
                (config.isDynamiteEnabled()
                        && matchesSkillItem(hand, config.getDynamiteMaterial(), config.getDynamiteNameKeyword()))
                || (config.isOverclockEnabled()
                        && matchesSkillItem(hand, config.getOverclockMaterial(), config.getOverclockNameKeyword()));

        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) {
            // 스킬 아이템 들고 시도했는데 광부가 아니면 안내
            if (holdingSkillItem) {
                player.sendMessage("§c[광산] §f광부 직업이 아니므로 스킬을 사용할 수 없습니다.");
            }
            return;
        }

        if (tryUseDynamite(event, player, profile, hand)) {
            return;
        }
        if (tryUseOverclock(event, player, profile, hand)) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        if (!tnt.getPersistentDataContainer().has(dynamiteKey, PersistentDataType.BYTE)) {
            return;
        }

        Player player = resolveDynamiteOwner(tnt);
        PlayerJobProfile profile = player != null
                ? core.getPlayerProfiles().getCached(player.getUniqueId())
                : null;

        List<Block> regenOres = new ArrayList<>();
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            iterator.remove();

            if (!regenBlockRegistry.isRegenBlock(block)) {
                continue;
            }
            // ★ 리전 밖 블록은 폭발 보상 대상 아님
            if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) {
                continue;
            }
            if (!MineOreMaterials.isOre(block.getType())) {
                continue;
            }
            regenOres.add(block);
        }

        if (regenOres.isEmpty()) {
            return;
        }

        if (player == null || profile == null || profile.getJobId() != JobId.MINER) {
            return;
        }

        Player finalPlayer = player;
        PlayerJobProfile finalProfile = profile;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Block block : regenOres) {
                if (!regenBlockRegistry.isRegenBlock(block)) {
                    continue;
                }
                if (!MineOreMaterials.isOre(block.getType())) {
                    continue;
                }
                RegenBlockEntry entry = regenBlockRegistry.getEntry(block);
                if (entry == null) {
                    continue;
                }
                block.setType(Material.AIR, false);
                rewardService.deliverRewards(finalPlayer, finalProfile, block, entry);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearForPlayer(event.getPlayer());
    }

    private boolean tryUseDynamite(
            PlayerInteractEvent event,
            Player player,
            PlayerJobProfile profile,
            ItemStack hand
    ) {
        if (!config.isDynamiteEnabled()) return false;
        // 다이너마이트 아이템 매칭 먼저 — 아이템이 아니면 조용히 통과 (오버클럭에 양보)
        if (!matchesSkillItem(hand, config.getDynamiteMaterial(), config.getDynamiteNameKeyword())) {
            return false;
        }
        // 아이템 일치 — 이제부터는 다이너마이트 시도로 간주, 실패 시 메시지로 알림
        if (player.getCooldown(config.getDynamiteMaterial()) > 0) {
            int remaining = player.getCooldown(config.getDynamiteMaterial());
            player.sendMessage("§c[광산] §f다이너마이트 쿨타임: " + (remaining / 20) + "초");
            event.setCancelled(true);
            return true;
        }

        Location spawnLoc = resolveSpawnLocation(player);
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            return false;
        }
        // ★ 다이너마이트는 광산 지역(리전 안)에서만 투척 가능
        if (!worldGuard.isInAnyRegion(spawnLoc, config.getMineAllowedRegions())) {
            player.sendMessage("§c[광산] §f광산 지역(WorldGuard)에서만 다이너마이트를 사용할 수 있습니다.");
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);

        spawnLoc.getWorld().spawn(spawnLoc, TNTPrimed.class, primed -> {
            primed.setFuseTicks(config.getDynamiteFuseTicks());
            primed.setSource(player);
            primed.getPersistentDataContainer().set(dynamiteKey, PersistentDataType.BYTE, (byte) 1);
            primed.getPersistentDataContainer().set(dynamiteOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            primed.setVelocity(new Vector(0, 0.05, 0));
        });

        int cooldownTicks = Math.max(0, config.getDynamiteCooldownTicks());
        if (cooldownTicks > 0) {
            player.setCooldown(config.getDynamiteMaterial(), cooldownTicks);
        }

        if (config.isDynamiteConsumeItem()) {
            hand.setAmount(hand.getAmount() - 1);
        }

        player.sendMessage("§e[광산] §f다이너마이트 투척!");
        return true;
    }

    private boolean tryUseOverclock(
            PlayerInteractEvent event,
            Player player,
            PlayerJobProfile profile,
            ItemStack hand
    ) {
        if (!config.isOverclockEnabled()) return false;
        if (!matchesSkillItem(hand, config.getOverclockMaterial(), config.getOverclockNameKeyword())) {
            return false;
        }
        // 아이템 일치 — 이제부터는 오버클럭 시도로 간주, 실패 사유를 메시지로 안내

        int jobLevel = profile.getLevel();
        if (jobLevel < config.getOverclockMinJobLevel() || jobLevel > config.getOverclockMaxJobLevel()) {
            player.sendMessage(legacyAmpersand(config.getOverclockLevelDeniedMessage()));
            event.setCancelled(true);
            return true;
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        Long activeEnd = overclockActiveUntilMs.get(id);
        if (activeEnd != null && now < activeEnd) {
            long newEnd = now + config.getOverclockDurationTicks() * MS_PER_TICK;
            overclockActiveUntilMs.put(id, newEnd);
            // 기존 Haste를 먼저 제거한 뒤 재부여하여 duration이 올바르게 적용되도록
            clearOverclockHaste(player);
            applyOverclockHaste(player);
            event.setCancelled(true);
            return true;
        }

        Long cdEnd = overclockCooldownUntilMs.get(id);
        if (cdEnd != null && now < cdEnd) {
            player.sendMessage(legacyAmpersand(config.getOverclockCooldownMessage()));
            event.setCancelled(true);
            return true;
        }

        long durationMs = config.getOverclockDurationTicks() * MS_PER_TICK;
        long cooldownMs = config.getOverclockCooldownTicks() * MS_PER_TICK;
        overclockActiveUntilMs.put(id, now + durationMs);
        overclockCooldownUntilMs.put(id, now + cooldownMs);
        applyOverclockHaste(player);

        event.setCancelled(true);
        return true;
    }

    private void applyOverclockHaste(Player player) {
        int durationTicks = config.getOverclockDurationTicks();
        int amplifier = config.getOverclockHasteAmplifier();
        PotionEffectType haste = PotionEffectType.HASTE;
        player.addPotionEffect(new PotionEffect(
                haste,
                durationTicks,
                amplifier,
                false,
                false,
                true
        ));
        player.getPersistentDataContainer().set(overclockHasteKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void clearOverclockHaste(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(overclockHasteKey, PersistentDataType.BYTE)) {
            return;
        }
        player.removePotionEffect(PotionEffectType.HASTE);
        pdc.remove(overclockHasteKey);
    }

    private void tickOverclockActionBar() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(overclockActiveUntilMs.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                overclockActiveUntilMs.remove(id);
                continue;
            }
            Long end = overclockActiveUntilMs.get(id);
            if (end == null) {
                continue;
            }
            if (now >= end) {
                clearOverclockHaste(player);
                overclockActiveUntilMs.remove(id);
                player.sendActionBar(Component.empty());
                continue;
            }
            double seconds = (end - now) / 1000.0;
            String text = String.format("&e오버클럭 &7| &f남은 시간 &b%.1f초", seconds);
            player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
        }
    }

    private static boolean matchesSkillItem(ItemStack hand, Material material, String nameKeyword) {
        if (hand == null || hand.getType() != material) {
            return false;
        }
        if (nameKeyword == null || nameKeyword.isBlank()) {
            return true;
        }
        if (!hand.hasItemMeta()) {
            return false;
        }
        org.bukkit.inventory.meta.ItemMeta meta = hand.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return plain.contains(nameKeyword);
    }

    private Player resolveDynamiteOwner(TNTPrimed tnt) {
        if (tnt.getSource() instanceof Player player) {
            return player;
        }
        String raw = tnt.getPersistentDataContainer().get(dynamiteOwnerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Location resolveSpawnLocation(Player player) {
        RayTraceResult trace = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                32.0,
                FluidCollisionMode.NEVER,
                true
        );
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize().multiply(1.2);
        if (trace != null && trace.getHitBlock() != null) {
            return trace.getHitBlock().getRelative(trace.getHitBlockFace()).getLocation().add(0.5, 0.1, 0.5);
        }
        return eye.clone().add(dir);
    }

    private static Component legacyAmpersand(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}

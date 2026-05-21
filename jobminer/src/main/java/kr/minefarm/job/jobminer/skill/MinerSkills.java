package kr.minefarm.job.jobminer.skill;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.mining.MineOreMaterials;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
import kr.minefarm.job.jobminer.message.MinerMessages;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import kr.minefarm.job.jobminer.mining.RegenMineRewardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private final PickaxeValidator pickaxeValidator;
    private final MinerMessages messages;
    private final NamespacedKey dynamiteKey;
    private final NamespacedKey dynamiteOwnerKey;
    private final NamespacedKey overclockHasteKey;

    private final Map<UUID, Long> overclockActiveUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> overclockCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dynamiteCooldownUntilMs = new ConcurrentHashMap<>();
    private BukkitTask overclockUiTask;

    public MinerSkills(
            JavaPlugin plugin,
            JobMinerConfig config,
            JobCoreAPI core,
            RegenBlockRegistry regenBlockRegistry,
            RegenMineRewardService rewardService,
            WorldGuardBridge worldGuard,
            PickaxeValidator pickaxeValidator,
            MinerMessages messages
    ) {
        this.plugin = plugin;
        this.config = config;
        this.core = core;
        this.regenBlockRegistry = regenBlockRegistry;
        this.rewardService = rewardService;
        this.worldGuard = worldGuard;
        this.pickaxeValidator = pickaxeValidator;
        this.messages = messages;
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

    /**
     * 광부 스킬 발동 — 광부 곡괭이가 트리거.
     * <ul>
     *   <li>우클릭 (sneak X) → 오버클럭 (unlock-level 1 이상)</li>
     *   <li>시프트 + 우클릭 → 다이너마이트 (unlock-level 30 이상)</li>
     * </ul>
     * 둘 다 worldguard.allowed-regions 안에서만 발동.
     * 리전 밖에서는 메시지 없이 silent 무시.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        // 트리거 = 광부 곡괭이만 (광부 직업이 아닌 사람의 곡괭이는 통과시키지 않음)
        if (!pickaxeValidator.isValidPickaxe(player)) return;

        // 광부 직업이 아니면 silent (일반 우클릭 동작 유지)
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) return;

        // ★ 리전 밖이면 silent — 메시지/cancel 없음. 일반 우클릭처럼 동작
        if (!worldGuard.isInAnyRegion(player.getLocation(), config.getMineAllowedRegions())) return;

        // 시프트 + 우클릭 → 다이너마이트, 일반 우클릭 → 오버클럭
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (player.isSneaking()) {
            tryUseDynamite(event, player, profile, hand);
        } else {
            tryUseOverclock(event, player, profile, hand);
        }
    }

    /** SKILL 스탯 기반 쿨다운 감소율 (0.0~cap) */
    private static double resolveCooldownReduction(int skillLevel, double perLevel, double cap) {
        double reduction = perLevel * skillLevel;
        if (reduction < 0) return 0;
        if (cap > 0 && reduction > cap) return cap;
        return reduction;
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

        // 해금 레벨 검사 (기본 30)
        int unlock = config.getDynamiteUnlockLevel();
        if (profile.getLevel() < unlock) {
            player.sendMessage(messages.format("dynamite-level-denied", java.util.Map.of("level", String.valueOf(unlock))));
            event.setCancelled(true);
            return true;
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long cdEnd = dynamiteCooldownUntilMs.get(id);
        if (cdEnd != null && now < cdEnd) {
            long remainingSec = (cdEnd - now + 999) / 1000;
            player.sendMessage(messages.format("dynamite-cooldown", java.util.Map.of("seconds", String.valueOf(remainingSec))));
            event.setCancelled(true);
            return true;
        }

        // ★ 제자리 설치 — 시선/위치 추적 없이 플레이어 발 위치에 그대로
        Location spawnLoc = player.getLocation().clone().add(0, 0.1, 0);
        if (spawnLoc.getWorld() == null) {
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);
        spawnLoc.getWorld().spawn(spawnLoc, TNTPrimed.class, primed -> {
            primed.setFuseTicks(config.getDynamiteFuseTicks());
            primed.setSource(player);
            primed.getPersistentDataContainer().set(dynamiteKey, PersistentDataType.BYTE, (byte) 1);
            primed.getPersistentDataContainer().set(dynamiteOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            // 제자리에 그대로 — velocity 0
            primed.setVelocity(new Vector(0, 0, 0));
        });

        // SKILL 스탯으로 쿨다운 감소
        int skillLevel = profile.getStatLevel(StatType.SKILL);
        double reduction = resolveCooldownReduction(
                skillLevel,
                config.getDynamiteCooldownReductionPerSkill(),
                config.getDynamiteCooldownReductionCap()
        );
        int cooldownTicks = Math.max(0,
                (int) Math.round(config.getDynamiteCooldownTicks() * (1.0 - reduction)));
        if (cooldownTicks > 0) {
            dynamiteCooldownUntilMs.put(id, now + (long) cooldownTicks * MS_PER_TICK);
        }

        player.sendMessage(messages.format("dynamite-thrown"));
        return true;
    }

    private boolean tryUseOverclock(
            PlayerInteractEvent event,
            Player player,
            PlayerJobProfile profile,
            ItemStack hand
    ) {
        if (!config.isOverclockEnabled()) return false;

        int jobLevel = profile.getLevel();
        int unlock = config.getOverclockUnlockLevel();
        if (jobLevel < unlock || jobLevel > config.getOverclockMaxJobLevel()) {
            player.sendMessage(messages.format("overclock-level-denied"));
            event.setCancelled(true);
            return true;
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        int skillLevel = profile.getStatLevel(StatType.SKILL);

        // 이미 활성 중 — 80레벨 이상에서만 확률 기반 연장 시도
        Long activeEnd = overclockActiveUntilMs.get(id);
        if (activeEnd != null && now < activeEnd) {
            event.setCancelled(true);
            if (jobLevel < config.getOverclockExtensionUnlockLevel()) {
                // 메카닉 미해금 — 그냥 쿨타임처럼 막음
                player.sendMessage(messages.format("overclock-cooldown"));
                return true;
            }
            tryOverclockExtension(player, profile, skillLevel, activeEnd);
            return true;
        }

        // 쿨다운 검사
        Long cdEnd = overclockCooldownUntilMs.get(id);
        if (cdEnd != null && now < cdEnd) {
            player.sendMessage(messages.format("overclock-cooldown"));
            event.setCancelled(true);
            return true;
        }

        // SKILL 스탯 기반 쿨다운 감소
        double cdReduction = resolveCooldownReduction(
                skillLevel,
                config.getOverclockCooldownReductionPerSkill(),
                config.getOverclockCooldownReductionCap()
        );
        long cooldownMs = (long) Math.max(0,
                Math.round(config.getOverclockCooldownTicks() * (1.0 - cdReduction))) * MS_PER_TICK;

        // SKILL 스탯 기반 지속시간 보너스
        long durationMs = computeOverclockDurationMs(skillLevel);

        overclockActiveUntilMs.put(id, now + durationMs);
        overclockCooldownUntilMs.put(id, now + cooldownMs);
        applyOverclockHaste(player, durationMs);
        playOverclockSound(player);

        event.setCancelled(true);
        return true;
    }

    /** SKILL 스탯 적용된 오버클럭 지속시간(ms) */
    private long computeOverclockDurationMs(int skillLevel) {
        double bonus = resolveCooldownReduction(  // 동일 공식 — min(cap, perLevel × skill)
                skillLevel,
                config.getOverclockDurationBonusPerSkill(),
                config.getOverclockDurationBonusCap()
        );
        long baseTicks = config.getOverclockDurationTicks();
        long finalTicks = Math.max(0, Math.round(baseTicks * (1.0 + bonus)));
        return finalTicks * MS_PER_TICK;
    }

    /**
     * 오버클럭 80레벨+ 메카닉 — active 중 재사용 시 확률 판정 → 성공 시 연장.
     * 실패 시 active/쿨다운 유지, 메시지만 표시.
     */
    private void tryOverclockExtension(Player player, PlayerJobProfile profile, int skillLevel, long activeEnd) {
        UUID id = player.getUniqueId();

        // 확률 = min(cap, base + perSkill × SKILL)
        double chance = Math.min(
                config.getOverclockExtensionChanceCap(),
                config.getOverclockExtensionBaseChance()
                        + config.getOverclockExtensionChancePerSkill() * skillLevel
        );
        if (chance < 0) chance = 0;

        // 연장량(ticks) = min(cap, base + perSkill × SKILL)
        int extTicks = Math.min(
                config.getOverclockExtensionTicksCap(),
                config.getOverclockExtensionBaseTicks()
                        + config.getOverclockExtensionTicksPerSkill() * skillLevel
        );
        if (extTicks < 0) extTicks = 0;

        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= chance) {
            // 실패
            int chancePercent = (int) Math.round(chance * 100);
            player.sendMessage(messages.format("overclock-extension-failure", java.util.Map.of("chance", String.valueOf(chancePercent))));
            return;
        }

        // 성공 — 지속시간 연장 + Haste 갱신 + 사운드
        long extendMs = (long) extTicks * MS_PER_TICK;
        long newEnd = activeEnd + extendMs;
        overclockActiveUntilMs.put(id, newEnd);
        clearOverclockHaste(player);
        applyOverclockHaste(player, newEnd - System.currentTimeMillis());
        playOverclockSound(player);

        int seconds = extTicks / 20;
        int chancePercent = (int) Math.round(chance * 100);
        player.sendMessage(messages.format("overclock-extension-success", java.util.Map.of(
                "seconds", String.valueOf(seconds),
                "chance", String.valueOf(chancePercent))));
    }

    /**
     * 오버클럭 발동 사운드 — config.activation-sound.
     * Paper 1.21에서 Sound가 Registry 기반으로 변경되어 NamespacedKey로 조회.
     * 미존재/잘못된 키일 경우 player.playSound(string) fallback.
     */
    private void playOverclockSound(Player player) {
        String name = config.getOverclockActivationSound();
        if (name == null || name.isBlank()) return;
        float vol = config.getOverclockActivationSoundVolume();
        float pitch = config.getOverclockActivationSoundPitch();

        // 1) Sound enum 이름 → Registry 조회 시도 (e.g. "ENTITY_BLAZE_SHOOT")
        try {
            NamespacedKey key = NamespacedKey.minecraft(
                    name.toLowerCase().replace('_', '.').replace("entity.", "entity.")
            );
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, vol, pitch);
                return;
            }
        } catch (Throwable ignored) {}

        // 2) 직접 NamespacedKey 문자열 (e.g. "minecraft:entity.blaze.shoot")
        try {
            String soundKey = name.contains(":") ? name :
                    "minecraft:" + name.toLowerCase().replace('_', '.');
            player.playSound(player.getLocation(), soundKey, vol, pitch);
        } catch (Throwable ignored) {
            // 마지막 fallback: Sound enum 직접 — 1.21에서도 enum 호환됨
            try {
                Sound fallback = Sound.valueOf(name.toUpperCase());
                player.playSound(player.getLocation(), fallback, vol, pitch);
            } catch (Throwable ignored2) {}
        }
    }

    private void applyOverclockHaste(Player player, long durationMs) {
        int durationTicks = (int) Math.max(1, durationMs / MS_PER_TICK);
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

    private static Component legacyAmpersand(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}

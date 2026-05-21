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
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.Set;
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
    /** 현재 살아있는 다이너마이트 TNT entity UUID — 종료/오류 시 cleanup용 */
    private final Set<UUID> activeDynamites = ConcurrentHashMap.newKeySet();
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
        dynamiteCooldownUntilMs.remove(id);
        clearOverclockHaste(player);
        player.sendActionBar(Component.empty());
    }

    /**
     * 플러그인 비활성/리로드/서버 종료 시 호출.
     * 살아있는 다이너마이트 TNT 모두 안전하게 제거 + 잔여 스캔.
     */
    public void shutdown() {
        if (overclockUiTask != null) {
            overclockUiTask.cancel();
            overclockUiTask = null;
        }
        overclockActiveUntilMs.clear();
        overclockCooldownUntilMs.clear();
        dynamiteCooldownUntilMs.clear();

        // ① 추적 중인 활성 TNT — 빠른 경로
        for (UUID id : activeDynamites) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        activeDynamites.clear();

        // ② 안전망 — 모든 로드된 월드에서 우리 PDC 가진 TNT 잔여 제거
        //    (이전 서버 비정상 종료로 디스크에서 다시 로드된 케이스 대응)
        try {
            for (var world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof TNTPrimed tnt
                            && tnt.getPersistentDataContainer().has(dynamiteKey, PersistentDataType.BYTE)) {
                        tnt.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
            // shutdown 중 월드 접근 실패는 무시 (서버 상태에 따라 다름)
        }
    }

    /**
     * 청크 로드 시 우리 다이너마이트 PDC 가진 TNT 가 디스크에서 다시 로드되면 즉시 제거.
     * (서버 비정상 종료된 케이스 → fuse 정보가 손상되어 의도치 않은 위치에서 폭발할 수 있음)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof TNTPrimed tnt
                    && tnt.getPersistentDataContainer().has(dynamiteKey, PersistentDataType.BYTE)) {
                tnt.remove();
                activeDynamites.remove(tnt.getUniqueId());
            }
        }
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

    /**
     * SKILL 스탯 기반 ticks 감소량 (초 단위 옵션 우선, fallback 비율).
     * absoluteSecondsPerSkill 가 0보다 크면 절대값으로 계산 (1스탯당 N초 감소).
     * 그렇지 않으면 ratio (비율 cap 적용) 사용.
     * 결과 ticks 가 minTicks 미만이면 minTicks 반환.
     */
    private static long resolveCooldownTicks(
            int baseTicks, int skillLevel,
            double secondsPerSkill, double ratioPerSkill, double ratioCap,
            double minSeconds
    ) {
        long minTicks = Math.max(0, Math.round(minSeconds * 20));
        long resultTicks;
        if (secondsPerSkill > 0.0) {
            long reductionTicks = Math.round(secondsPerSkill * skillLevel * 20);
            resultTicks = baseTicks - reductionTicks;
        } else {
            double ratio = resolveCooldownReduction(skillLevel, ratioPerSkill, ratioCap);
            resultTicks = Math.round(baseTicks * (1.0 - ratio));
        }
        return Math.max(minTicks, resultTicks);
    }

    /**
     * SKILL 스탯 기반 지속시간 ticks (초 단위 절대값 우선, fallback 비율).
     * maxSeconds=0 이면 무제한.
     */
    private static long resolveDurationTicks(
            int baseTicks, int skillLevel,
            double secondsPerSkill, double ratioPerSkill, double ratioCap,
            double maxSeconds
    ) {
        long resultTicks;
        if (secondsPerSkill > 0.0) {
            long bonusTicks = Math.round(secondsPerSkill * skillLevel * 20);
            resultTicks = baseTicks + bonusTicks;
        } else {
            double ratio = resolveCooldownReduction(skillLevel, ratioPerSkill, ratioCap);
            resultTicks = Math.round(baseTicks * (1.0 + ratio));
        }
        if (maxSeconds > 0.0) {
            long cap = Math.round(maxSeconds * 20);
            if (resultTicks > cap) resultTicks = cap;
        }
        return Math.max(0, resultTicks);
    }

    /**
     * 다이너마이트 폭발 처리 — fuse 끝 시점에 직접 호출 (BukkitRunnable).
     * EntityExplodeEvent 에 의존하지 않음 (yield 와 무관하게 동작 보장).
     *
     * 순서:
     *   ① TNT 엔티티 remove + 추적 해제
     *   ② 시각/사운드 효과 (폭발 사운드 + 파티클)
     *   ③ 위치 기준 size×size×size 정육면체 내 리젠 광물 찾아 본인이 캔 것처럼 처리
     *      (block.setType(AIR) + rewardService.deliverRewards — cobblestone 대체,
     *       drops, 자동판매, 경험치, regen 예약까지 정상 흐름)
     */
    private void handleDynamiteDetonation(UUID tntId, UUID playerId) {
        Entity entity = plugin.getServer().getEntity(tntId);
        activeDynamites.remove(tntId);
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        entity.remove();
        if (world == null) return;

        // 시각/사운드 효과 (실제 폭발은 일어나지 않으므로 직접 재생)
        try {
            world.playSound(loc, "minecraft:entity.generic.explode", 4.0f, 1.0f);
        } catch (Throwable ignored) {}
        try {
            Particle p;
            try { p = Particle.valueOf("EXPLOSION_EMITTER"); }
            catch (Throwable ignored) { p = Particle.valueOf("EXPLOSION"); }
            world.spawnParticle(p, loc, 1);
        } catch (Throwable ignored) {}

        // 플레이어/프로필 검증
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(playerId);
        if (profile == null || profile.getJobId() != JobId.MINER) return;

        // size×size×size 정육면체 스캔 (기본 4 → 4×4×4 = 64 블록)
        int size = Math.max(1, config.getDynamiteExplosionSize());
        int halfDown = size / 2;
        int halfUp = size - halfDown;
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        for (int dx = -halfDown; dx < halfUp; dx++) {
            for (int dy = -halfDown; dy < halfUp; dy++) {
                for (int dz = -halfDown; dz < halfUp; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!regenBlockRegistry.isRegenBlock(block)) continue;
                    if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) continue;
                    if (!MineOreMaterials.isOre(block.getType())) continue;
                    RegenBlockEntry entry = regenBlockRegistry.getEntry(block);
                    if (entry == null) continue;
                    // 본인이 캔 것처럼 처리 — deliverRewards 가 cobblestone 대체 + regen 예약까지 다 함
                    block.setType(Material.AIR, false);
                    rewardService.deliverRewards(player, profile, block, entry);
                }
            }
        }
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
        TNTPrimed primed = spawnLoc.getWorld().spawn(spawnLoc, TNTPrimed.class, t -> {
            t.setFuseTicks(config.getDynamiteFuseTicks());
            t.setSource(player);
            t.getPersistentDataContainer().set(dynamiteKey, PersistentDataType.BYTE, (byte) 1);
            t.getPersistentDataContainer().set(dynamiteOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            // 제자리에 그대로 — velocity 0. fuse 동안 깜빡임/불꽃 모션은 TNT 엔티티 기본 동작.
            t.setVelocity(new Vector(0, 0, 0));
            // 불 점화만 비활성화 (yield 는 건드리지 않음 — EntityExplodeEvent 의존 안 하므로 무관)
            t.setIsIncendiary(false);
        });
        // 활성 TNT 추적 — shutdown/오류 시 안전 cleanup
        UUID tntId = primed.getUniqueId();
        activeDynamites.add(tntId);

        // ★ fuse 끝 시점에 우리가 직접 처리 — EntityExplodeEvent 의존 X
        //   TNT 자체 폭발 직전(fuse-1)에 제거하여 진짜 폭발 부산물 차단.
        UUID playerId = player.getUniqueId();
        int fuseTicks = Math.max(1, config.getDynamiteFuseTicks() - 1);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> handleDynamiteDetonation(tntId, playerId),
                fuseTicks);

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

        // SKILL 스탯 기반 — 초 단위 옵션 우선
        long cooldownTicks = resolveCooldownTicks(
                config.getOverclockCooldownTicks(),
                skillLevel,
                config.getOverclockCooldownReductionSecondsPerSkill(),
                config.getOverclockCooldownReductionPerSkill(),
                config.getOverclockCooldownReductionCap(),
                config.getOverclockCooldownMinSeconds()
        );
        long durationTicks = resolveDurationTicks(
                config.getOverclockDurationTicks(),
                skillLevel,
                config.getOverclockDurationBonusSecondsPerSkill(),
                config.getOverclockDurationBonusPerSkill(),
                config.getOverclockDurationBonusCap(),
                config.getOverclockDurationMaxSeconds()
        );
        long cooldownMs = cooldownTicks * MS_PER_TICK;
        long durationMs = durationTicks * MS_PER_TICK;

        overclockActiveUntilMs.put(id, now + durationMs);
        overclockCooldownUntilMs.put(id, now + cooldownMs);
        applyOverclockHaste(player, durationMs);
        playOverclockSound(player);

        // 발동 메시지
        player.sendMessage(messages.format("overclock-activated", java.util.Map.of(
                "duration", formatSeconds(durationTicks / 20.0),
                "cooldown", formatSeconds(cooldownTicks / 20.0))));

        event.setCancelled(true);
        return true;
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

        // 연장량(ticks) — seconds-per-skill 우선, fallback ticks-per-skill
        double extSecPerSkill = config.getOverclockExtensionSecondsPerSkill();
        int extTicks;
        if (extSecPerSkill > 0.0) {
            extTicks = config.getOverclockExtensionBaseTicks()
                    + (int) Math.round(extSecPerSkill * skillLevel * 20);
        } else {
            extTicks = config.getOverclockExtensionBaseTicks()
                    + config.getOverclockExtensionTicksPerSkill() * skillLevel;
        }
        int extCap = config.getOverclockExtensionTicksCap();
        if (extTicks > extCap) extTicks = extCap;
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
     * Paper 버전 차이를 피하기 위해 String 기반 playSound 만 사용.
     * config 값 예시:
     *   ENTITY_BLAZE_SHOOT          → minecraft:entity.blaze.shoot 로 변환
     *   minecraft:entity.blaze.shoot → 그대로 사용
     *   entity.blaze.shoot           → minecraft: prefix 자동 부여
     */
    private void playOverclockSound(Player player) {
        String name = config.getOverclockActivationSound();
        if (name == null || name.isBlank()) return;
        float vol = config.getOverclockActivationSoundVolume();
        float pitch = config.getOverclockActivationSoundPitch();

        String soundKey;
        if (name.contains(":")) {
            soundKey = name.toLowerCase();
        } else if (name.contains(".")) {
            soundKey = "minecraft:" + name.toLowerCase();
        } else {
            // ENTITY_BLAZE_SHOOT → minecraft:entity.blaze.shoot
            soundKey = "minecraft:" + name.toLowerCase().replace('_', '.');
        }

        try {
            player.playSound(player.getLocation(), soundKey, vol, pitch);
        } catch (Throwable ignored) {
            // 잘못된 사운드 키 — 조용히 무시
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
        if (overclockActiveUntilMs.isEmpty()) return; // 비어있으면 즉시 종료
        long now = System.currentTimeMillis();
        // ConcurrentHashMap iterator 는 weakly-consistent — 매 틱 ArrayList 복사 불필요.
        var iter = overclockActiveUntilMs.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            UUID id = entry.getKey();
            Long end = entry.getValue();
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                iter.remove();
                continue;
            }
            if (end == null) {
                continue;
            }
            if (now >= end) {
                clearOverclockHaste(player);
                iter.remove();
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

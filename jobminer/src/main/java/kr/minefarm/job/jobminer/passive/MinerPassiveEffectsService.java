package kr.minefarm.job.jobminer.passive;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 광부 패시브 (가벼운 발걸음 — Speed).
 * <ul>
 *     <li>PDC에는 {@link PersistentDataType#BYTE} 한 바이트로 “우리가 부여한 Speed”만 표시한다.</li>
 *     <li>{@link #clear}: {@code removePotionEffect} 와 함께 PDC 키를 반드시 {@code remove} 해야 재적용 시 상태가 꼬이지 않는다.</li>
 * </ul>
 * <p>
 * {@link #apply} / {@link #clear} 는 JobCore 쪽에서 DB 비동기 로드 후 {@code runTask}로 호출되는 것을 전제로 한다 (메인 스레드 전용).
 */
public final class MinerPassiveEffectsService {

    /** 우리가 부여한 무한 Speed만 식별 (이 키가 있을 때만 제거) */
    private final NamespacedKey minerPassiveSpeedKey;

    private final JobMinerConfig minerConfig;

    public MinerPassiveEffectsService(JavaPlugin plugin, JobMinerConfig minerConfig) {
        this.minerConfig = minerConfig;
        this.minerPassiveSpeedKey = new NamespacedKey(plugin, "miner_passive_speed");
    }

    /**
     * 이전에 우리가 건 Speed가 있으면 제거한 뒤, 현재 프로필 기준으로 무한 Speed를 다시 부여한다.
     * 접속 직후·스탯 변경 직후 {@link kr.minefarm.job.jobminer.MinerJob#refreshPassiveEffects} 에서 호출된다.
     */
    public void apply(Player player, PlayerJobProfile profile) {
        if (profile == null || profile.getJobId() != JobId.MINER) {
            clear(player);
            return;
        }

        ConfigurationSection root = minerConfig.getMinerPassivesSection();
        ConfigurationSection lightFootsteps = root != null
                ? root.getConfigurationSection("light-footsteps")
                : null;
        if (lightFootsteps == null || !lightFootsteps.getBoolean("enabled", false)) {
            clear(player);
            return;
        }

        int amplifier = resolveSpeedAmplifier(lightFootsteps, profile);

        // 이전 우리 버프 제거 후 재부여 → 스탯 변경 시 중첩 방지
        removeOurSpeedIfTagged(player);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                PotionEffect.INFINITE_DURATION,
                amplifier,
                false,
                false,
                true
        ));
        player.getPersistentDataContainer().set(minerPassiveSpeedKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * PDC에 우리 키가 있을 때만 {@link Player#removePotionEffect(org.bukkit.potion.PotionEffectType)} 후
     * PDC 키를 {@link org.bukkit.persistence.PersistentDataContainer#remove(NamespacedKey)} 한다.
     */
    public void clear(Player player) {
        removeOurSpeedIfTagged(player);
    }

    private int resolveSpeedAmplifier(ConfigurationSection lightFootsteps, PlayerJobProfile profile) {
        int cap = Math.max(0, lightFootsteps.getInt("amplifier-cap", 3));
        int base = lightFootsteps.getInt("base-amplifier", 0);
        int perSkill = lightFootsteps.getInt("per-skill-level", 1);
        int perJobLevel = lightFootsteps.getInt("per-job-level", 0);
        int skill = profile.getStatLevel(StatType.SKILL);
        int jobLevel = profile.getLevel();
        long sum = (long) base + (long) skill * perSkill + (long) jobLevel * perJobLevel;
        return (int) Math.min(cap, Math.max(0, sum));
    }

    private void removeOurSpeedIfTagged(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(minerPassiveSpeedKey, PersistentDataType.BYTE)) {
            return;
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        pdc.remove(minerPassiveSpeedKey);
    }
}

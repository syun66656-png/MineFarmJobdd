package kr.minefarm.job.jobminer.passive;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
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
 * <p>
 * WorldGuard 리전 검사: {@code allowed-regions} 가 설정되어 있으면 해당 리전 안에서만 효과 유지.
 */
public final class MinerPassiveEffectsService {

    private final NamespacedKey minerPassiveSpeedKey;
    private final JobMinerConfig minerConfig;
    private final WorldGuardBridge worldGuard;

    public MinerPassiveEffectsService(JavaPlugin plugin, JobMinerConfig minerConfig, WorldGuardBridge worldGuard) {
        this.minerConfig = minerConfig;
        this.worldGuard = worldGuard;
        this.minerPassiveSpeedKey = new NamespacedKey(plugin, "miner_passive_speed");
    }

    public void apply(Player player, PlayerJobProfile profile) {
        if (profile == null || profile.getJobId() != JobId.MINER) {
            clear(player);
            return;
        }
        // WorldGuard 리전 체크 (allowed-regions 비어 있으면 무제한 통과)
        if (!worldGuard.isInAnyRegion(player.getLocation(), minerConfig.getPassiveAllowedRegions())) {
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

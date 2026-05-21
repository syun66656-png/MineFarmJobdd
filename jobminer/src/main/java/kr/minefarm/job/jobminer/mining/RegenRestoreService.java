package kr.minefarm.job.jobminer.mining;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채굴 후 지연 복구 및 관리자 즉시 초기화.
 * <p>
 * 채굴 즉시 대체 블록(예: COBBLESTONE)으로 교체한 뒤,
 * {@code runTaskLater} 완료 후 원래 블록으로 복구.
 */
public final class RegenRestoreService {

    private final JavaPlugin plugin;
    private final JobMinerConfig config;
    private final RegenBlockRegistry regenBlockRegistry;
    private final Map<RegenBlockEntry.BlockKey, Integer> pendingTaskIds = new ConcurrentHashMap<>();

    public RegenRestoreService(
            JavaPlugin plugin,
            JobMinerConfig config,
            RegenBlockRegistry regenBlockRegistry
    ) {
        this.plugin = plugin;
        this.config = config;
        this.regenBlockRegistry = regenBlockRegistry;
    }

    public void scheduleRestore(Block block, RegenBlockEntry entry) {
        if (entry == null) return;
        RegenBlockEntry.BlockKey key = entry.key();
        cancelPending(key);

        // ★ 채굴 직후 즉시 대체 블록 배치
        if (config.isRegenReplacementEnabled() && block != null) {
            Material replacement = config.getRegenReplacementMaterial();
            if (replacement != null && replacement != Material.AIR) {
                // 다음 tick에 setType (BlockBreakEvent 처리 직후 적용)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Block b = entry.resolveBlock();
                    if (b != null) b.setType(replacement, false);
                });
            }
        }

        long delayTicks = config.getRegenDelaySeconds() * 20L;
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restoreEntry(entry);
            pendingTaskIds.remove(key);
        }, delayTicks).getTaskId();
        pendingTaskIds.put(key, taskId);
    }

    public boolean resetNow(RegenBlockEntry entry) {
        if (entry == null) return false;
        cancelPending(entry.key());
        return restoreEntry(entry);
    }

    public int resetAll() {
        int count = 0;
        for (RegenBlockEntry entry : regenBlockRegistry.getAllEntries()) {
            if (resetNow(entry)) count++;
        }
        return count;
    }

    private boolean restoreEntry(RegenBlockEntry entry) {
        Block block = entry.resolveBlock();
        if (block == null) return false;
        // 대체 블록 / AIR / 다른 블록 어떤 상태든 원본으로 복구
        if (block.getBlockData().matches(entry.createBlockData())) return true;
        entry.applyTo(block);
        return true;
    }

    private void cancelPending(RegenBlockEntry.BlockKey key) {
        Integer taskId = pendingTaskIds.remove(key);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    /** 모듈 비활성화·리로드 시 대기 중인 복구 태스크를 모두 취소. */
    public void cancelAllPending() {
        for (Integer taskId : pendingTaskIds.values()) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        pendingTaskIds.clear();
    }
}

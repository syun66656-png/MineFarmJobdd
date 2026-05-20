package kr.minefarm.job.jobminer.mining;

import org.bukkit.block.Block;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 리젠 광산 블록 등록소. 채굴 중에도 좌표는 유지된다.
 */
public final class RegenBlockRegistry {

    private final Map<RegenBlockEntry.BlockKey, RegenBlockEntry> entries = new ConcurrentHashMap<>();
    private Runnable onChanged = () -> {
    };

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> {
        };
    }

    public boolean isRegenBlock(Block block) {
        return entries.containsKey(RegenBlockEntry.BlockKey.of(block));
    }

    public RegenBlockEntry getEntry(Block block) {
        return entries.get(RegenBlockEntry.BlockKey.of(block));
    }

    public RegenBlockEntry register(Block block) {
        RegenBlockEntry entry = RegenBlockEntry.from(block);
        entries.put(entry.key(), entry);
        onChanged.run();
        return entry;
    }

    public boolean unregister(Block block) {
        boolean removed = entries.remove(RegenBlockEntry.BlockKey.of(block)) != null;
        if (removed) {
            onChanged.run();
        }
        return removed;
    }

    public Collection<RegenBlockEntry> getAllEntries() {
        return entries.values();
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
        onChanged.run();
    }

    public void loadAll(Collection<RegenBlockEntry> loaded) {
        entries.clear();
        for (RegenBlockEntry entry : loaded) {
            entries.put(entry.key(), entry);
        }
    }
}

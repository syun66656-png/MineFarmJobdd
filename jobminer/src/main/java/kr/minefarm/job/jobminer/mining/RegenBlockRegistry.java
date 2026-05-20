package kr.minefarm.job.jobminer.mining;

import org.bukkit.block.Block;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 리젠 광산 블록 등록소.
 * <p>
 * 변경 시 {@link #onChanged} 콜백을 즉시 호출하는 대신 dirty 플래그를 세우고,
 * 호출부(JobMinerModule)가 주기적으로 {@link #isDirty()}/{@link #clearDirty()}를
 * 확인해 비동기 저장을 수행한다. 매 등록/해제마다 동기 I/O 가 발생하는 문제를 방지한다.
 */
public final class RegenBlockRegistry {

    private final Map<RegenBlockEntry.BlockKey, RegenBlockEntry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** @deprecated 디바운스 저장으로 교체. isDirty()/clearDirty() 를 사용하라. */
    @Deprecated
    private Runnable onChanged = () -> {};

    /**
     * 변경 감지용 dirty 플래그.
     *
     * @return 마지막 clearDirty() 이후 변경이 있었으면 true
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /** dirty 플래그를 해제한다 (저장 완료 후 호출). */
    public void clearDirty() {
        dirty.set(false);
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
        dirty.set(true);
        return entry;
    }

    public boolean unregister(Block block) {
        boolean removed = entries.remove(RegenBlockEntry.BlockKey.of(block)) != null;
        if (removed) dirty.set(true);
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
        dirty.set(true);
    }

    public void loadAll(Collection<RegenBlockEntry> loaded) {
        entries.clear();
        for (RegenBlockEntry entry : loaded) {
            entries.put(entry.key(), entry);
        }
        dirty.set(false); // 로드 직후는 저장 불필요
    }

    // ── 하위 호환 ────────────────────────────────────────────────────────────

    /** @deprecated 디바운스 저장 패턴으로 교체되어 호출이 무의미해짐. */
    @Deprecated
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged != null ? onChanged : () -> {};
    }
}

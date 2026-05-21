package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 광물 머티리얼별 채굴 경험치 범위 테이블.
 * <p>
 * config 형식:
 * <pre>
 * mine-exp:
 *   IRON_ORE: "20~35"    # 20~35 사이 랜덤
 *   DIAMOND_ORE: 500     # 항상 500
 *   STONE: "1~3"
 * </pre>
 * key가 없는 머티리얼은 0 EXP.
 */
public final class MineExpTable {

    public record ExpRange(long min, long max) {
        public long roll() {
            if (min >= max) return min;
            return ThreadLocalRandom.current().nextLong(min, max + 1);
        }

        /** "20~35" 또는 "500" 파싱 */
        public static ExpRange parse(String raw) {
            if (raw == null || raw.isBlank()) return new ExpRange(0, 0);
            String trimmed = raw.trim();
            int tilde = trimmed.indexOf('~');
            try {
                if (tilde < 0) {
                    long val = Long.parseLong(trimmed);
                    return new ExpRange(val, val);
                }
                long min = Long.parseLong(trimmed.substring(0, tilde).trim());
                long max = Long.parseLong(trimmed.substring(tilde + 1).trim());
                return new ExpRange(Math.min(min, max), Math.max(min, max));
            } catch (NumberFormatException e) {
                return new ExpRange(0, 0);
            }
        }

        public boolean hasExp() {
            return max > 0;
        }
    }

    /** Material → ExpRange 테이블 */
    private final Map<Material, ExpRange> table;

    public MineExpTable(Map<Material, ExpRange> table) {
        this.table = Collections.unmodifiableMap(table);
    }

    /**
     * 해당 머티리얼 채굴 시 지급할 경험치를 랜덤 추첨한다.
     * 테이블에 없으면 0 반환.
     */
    public long roll(Material material) {
        if (material == null) return 0;
        ExpRange range = table.get(material);
        return range != null ? range.roll() : 0;
    }

    /** 테이블에 해당 머티리얼이 등록됐는지 확인 */
    public boolean has(Material material) {
        return material != null && table.containsKey(material);
    }

    /** 전체 테이블 (GUI·명세서 표시용) */
    public Map<Material, ExpRange> getTable() {
        return table;
    }
}

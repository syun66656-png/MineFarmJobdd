package kr.minefarm.job.jobminer.mining;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MineExpTable + ExpRange")
class MineExpTableTest {

    @Nested
    @DisplayName("ExpRange.parse")
    class ParseTests {

        @ParameterizedTest(name = "\"{0}\" → min={1}, max={2}")
        @CsvSource({
            "100,     100, 100",
            "10~20,    10,  20",
            "20~10,    10,  20",   // min/max 자동 정렬
            "0~0,       0,   0",
            "500~500,  500, 500",
        })
        void 파싱_정확성(String raw, long expectedMin, long expectedMax) {
            var range = MineExpTable.ExpRange.parse(raw);
            assertEquals(expectedMin, range.min());
            assertEquals(expectedMax, range.max());
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.NullAndEmptySource
        void null_또는_빈값은_0(String raw) {
            var range = MineExpTable.ExpRange.parse(raw);
            assertEquals(0, range.min());
            assertEquals(0, range.max());
            assertFalse(range.hasExp());
        }

        @Test
        void 숫자가_아니면_0() {
            var range = MineExpTable.ExpRange.parse("abc~xyz");
            assertEquals(0, range.min());
        }
    }

    @Nested
    @DisplayName("ExpRange.roll")
    class RollTests {

        @Test
        void 고정값은_항상_같은_수() {
            var range = new MineExpTable.ExpRange(300, 300);
            for (int i = 0; i < 100; i++) {
                assertEquals(300, range.roll());
            }
        }

        @RepeatedTest(200)
        void 범위_내에서만_나옴() {
            var range = new MineExpTable.ExpRange(10, 20);
            long val = range.roll();
            assertTrue(val >= 10 && val <= 20,
                    "범위 10~20 에서 벗어난 값: " + val);
        }

        @Test
        void min이_max보다_크면_min_반환() {
            var range = new MineExpTable.ExpRange(50, 50);
            assertEquals(50, range.roll());
        }
    }

    @Nested
    @DisplayName("MineExpTable.roll")
    class TableTests {

        private MineExpTable makeTable() {
            Map<Material, MineExpTable.ExpRange> map = new EnumMap<>(Material.class);
            map.put(Material.COAL_ORE,    new MineExpTable.ExpRange(10, 20));
            map.put(Material.DIAMOND_ORE, new MineExpTable.ExpRange(200, 200));
            return new MineExpTable(map);
        }

        @Test
        void 등록된_머티리얼_경험치_반환() {
            MineExpTable table = makeTable();
            long diamond = table.roll(Material.DIAMOND_ORE);
            assertEquals(200, diamond);
        }

        @RepeatedTest(50)
        void 범위_항목_roll_범위_내() {
            MineExpTable table = makeTable();
            long val = table.roll(Material.COAL_ORE);
            assertTrue(val >= 10 && val <= 20,
                    "COAL_ORE 범위 10~20 에서 벗어남: " + val);
        }

        @Test
        void 미등록_머티리얼은_0() {
            MineExpTable table = makeTable();
            assertEquals(0, table.roll(Material.DIRT));
            assertEquals(0, table.roll(null));
        }

        @Test
        void has_등록여부_정확() {
            MineExpTable table = makeTable();
            assertTrue(table.has(Material.COAL_ORE));
            assertFalse(table.has(Material.STONE));
            assertFalse(table.has(null));
        }
    }
}

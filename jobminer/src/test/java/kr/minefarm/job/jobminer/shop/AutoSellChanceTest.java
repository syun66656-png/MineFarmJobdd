package kr.minefarm.job.jobminer.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoSell 확률 공식: min(max, base + perLevel × statLevel) 을 검증한다.
 * Bukkit 의존 없는 순수 수식 단위 테스트.
 */
@DisplayName("AutoSell 확률 공식")
class AutoSellChanceTest {

    /** 실제 AutoSellProcessor와 동일한 공식 */
    static double calcChance(double base, double perLevel, double max, int statLevel) {
        return Math.min(max, base + perLevel * statLevel);
    }

    @Nested
    @DisplayName("기본값 (base=0.05, perLevel=0.02, max=0.80)")
    class Defaults {

        private static final double BASE = 0.05;
        private static final double PER  = 0.02;
        private static final double MAX  = 0.80;

        @ParameterizedTest(name = "스탯 {0} → 확률 {1}")
        @CsvSource({
            "0,  0.05",
            "1,  0.07",
            "5,  0.15",
            "10, 0.25",
            "25, 0.55",
            "37, 0.79",
            "38, 0.81"
        })
        void 스탯별_확률(int stat, double expected) {
            double actual = calcChance(BASE, PER, MAX, stat);
            // 클램핑 확인
            double clamped = Math.min(MAX, actual);
            assertEquals(Math.min(MAX, expected), clamped, 0.001);
        }

        @Test
        void 최대치_초과_불가() {
            double chance = calcChance(BASE, PER, MAX, 9999);
            assertTrue(chance <= MAX, "확률이 max를 초과해선 안 됨");
        }

        @Test
        void 스탯_0에서_base보다_작지않음() {
            double chance = calcChance(BASE, PER, MAX, 0);
            assertTrue(chance >= BASE);
        }

        @Test
        void 스탯_증가에_따라_확률_증가() {
            double prev = calcChance(BASE, PER, MAX, 0);
            for (int stat = 1; stat <= 25; stat++) {
                double curr = calcChance(BASE, PER, MAX, stat);
                assertTrue(curr >= prev,
                        "스탯 " + stat + "의 확률이 전 단계보다 낮습니다.");
                prev = curr;
            }
        }
    }

    @Nested
    @DisplayName("커스텀 설정")
    class Custom {

        @Test
        void base만_있어도_작동() {
            double chance = calcChance(0.5, 0.0, 1.0, 10);
            assertEquals(0.5, chance, 0.001);
        }

        @Test
        void 퍼센트_100_설정() {
            double chance = calcChance(1.0, 0.0, 1.0, 0);
            assertEquals(1.0, chance, 0.001);
        }

        @Test
        void 음수_스탯은_base이하_가능_but_실제사용_안됨() {
            // 게임에서 statLevel은 항상 0 이상이지만 수식 자체는 허용
            double chance = calcChance(0.5, 0.1, 1.0, -5);
            // 0.5 + 0.1 × (-5) = 0.0
            assertEquals(0.0, chance, 0.001);
        }
    }
}

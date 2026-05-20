package kr.minefarm.job.jobminer.shop;

import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.bukkit.Material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MineSellCalculator — 판매가 계산")
class MineSellCalculatorTest {

    @Mock
    private JobMinerConfig config;

    private MineSellCalculator calculator;

    @BeforeEach
    void setUp() {
        // bonus-multiplier 기본값 0.05 (SELL스탯 1당 +5%)
        lenient().when(config.getSellBonusMultiplier()).thenReturn(0.05);
        calculator = new MineSellCalculator(config);
    }

    @Nested
    @DisplayName("기본 가격 계산")
    class BasePrice {

        @Test
        void 단가_10_수량_5_스탯0() {
            when(config.getShopBasePrice(Material.COAL)).thenReturn(10.0);
            double result = calculator.calculateTotalPrice(Material.COAL, 5, 0);
            assertEquals(50.0, result, 0.001);
        }

        @Test
        void 등록되지않은_아이템은_0() {
            when(config.getShopBasePrice(Material.DIRT)).thenReturn(0.0);
            assertEquals(0.0, calculator.calculateTotalPrice(Material.DIRT, 10, 5));
        }

        @Test
        void 수량_0이면_0() {
            assertEquals(0.0, calculator.calculateTotalPrice(Material.COAL, 0, 10));
        }

        @Test
        void 수량_음수이면_0() {
            assertEquals(0.0, calculator.calculateTotalPrice(Material.COAL, -1, 5));
        }
    }

    @Nested
    @DisplayName("SELL 스탯 보너스")
    class SellBonus {

        @BeforeEach
        void setupPrice() {
            when(config.getShopBasePrice(Material.DIAMOND)).thenReturn(120.0);
        }

        @ParameterizedTest(name = "SELL스탯 {0} → 총 {1}골드")
        @CsvSource({
            "0,  120.0",   // 보너스 없음
            "1,  126.0",   // +5%
            "2,  132.0",   // +10%
            "10, 180.0",   // +50%
            "20, 240.0",   // +100%
        })
        void 스탯별_보너스_계산(int sellStat, double expected) {
            double result = calculator.calculateTotalPrice(Material.DIAMOND, 1, sellStat);
            assertEquals(expected, result, 0.001,
                    "SELL스탯=" + sellStat + "일 때 가격 불일치");
        }

        @Test
        void 수량_배수_적용() {
            // 단가 120, 수량 5, 스탯 0 → 600
            double base = calculator.calculateTotalPrice(Material.DIAMOND, 5, 0);
            assertEquals(600.0, base, 0.001);

            // 단가 120, 수량 5, 스탯 1 → 630
            double withStat = calculator.calculateTotalPrice(Material.DIAMOND, 5, 1);
            assertEquals(630.0, withStat, 0.001);
        }
    }

    @Nested
    @DisplayName("formatPrice")
    class FormatPrice {

        @Test
        void 정수_금액은_소수점_없이() {
            assertEquals("1000", calculator.formatPrice(1000.0));
            assertEquals("0", calculator.formatPrice(0.0));
        }

        @Test
        void 소수_금액은_2자리() {
            assertEquals("10.50", calculator.formatPrice(10.5));
            assertEquals("1.23", calculator.formatPrice(1.234));
        }
    }

    @Nested
    @DisplayName("bonus-multiplier 변경 적용")
    class MultiplierVariant {

        @Test
        void multiplier_0이면_스탯_무효() {
            when(config.getSellBonusMultiplier()).thenReturn(0.0);
            when(config.getShopBasePrice(Material.COAL)).thenReturn(10.0);
            calculator = new MineSellCalculator(config);

            double r0  = calculator.calculateTotalPrice(Material.COAL, 1, 0);
            double r10 = calculator.calculateTotalPrice(Material.COAL, 1, 10);
            assertEquals(r0, r10, 0.001, "multiplier=0이면 스탯 관계없이 동일해야 함");
        }

        @Test
        void multiplier_0_1이면_스탯1당_10퍼센트() {
            when(config.getSellBonusMultiplier()).thenReturn(0.1);
            when(config.getShopBasePrice(Material.COAL)).thenReturn(100.0);
            calculator = new MineSellCalculator(config);

            double result = calculator.calculateTotalPrice(Material.COAL, 1, 5);
            // base=100, bonus=100×0.1×5=50 → 150
            assertEquals(150.0, result, 0.001);
        }
    }
}

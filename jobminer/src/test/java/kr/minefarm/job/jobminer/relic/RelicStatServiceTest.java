package kr.minefarm.job.jobminer.relic;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelicStatService")
class RelicStatServiceTest {

    @Mock
    private JobMinerConfig config;

    private RelicStatService service;
    private PlayerJobProfile profile;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("relic.bonus-drop-chance-per-level", 0.02);
        yaml.set("relic.bonus-drop-multiplier", 1.0);
        yaml.set("relic.exp-bonus-per-level", 0.02);
        // lenient: ExpBonus 내 테스트는 getRawConfig()를 호출하지 않음
        lenient().when(config.getRawConfig()).thenReturn(yaml);

        service = new RelicStatService(config);

        profile = new PlayerJobProfile(UUID.randomUUID());
        profile.setJobId(JobId.MINER);
    }

    // ── 경험치 보너스 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyExpBonus")
    class ExpBonus {

        @Test
        void RELIC_0이면_원본_경험치() {
            profile.setStatLevel(StatType.RELIC, 0);
            assertEquals(100L, service.applyExpBonus(100L, profile));
        }

        @Test
        void RELIC_10이면_20퍼센트_추가() {
            profile.setStatLevel(StatType.RELIC, 10);
            assertEquals(120L, service.applyExpBonus(100L, profile));
        }

        @Test
        void RELIC_25이면_50퍼센트_추가() {
            profile.setStatLevel(StatType.RELIC, 25);
            assertEquals(150L, service.applyExpBonus(100L, profile));
        }

        @Test
        void 기본경험치_0이면_그대로_0() {
            profile.setStatLevel(StatType.RELIC, 10);
            assertEquals(0L, service.applyExpBonus(0L, profile));
        }

        @Test
        void 음수_경험치는_그대로_반환() {
            assertEquals(-1L, service.applyExpBonus(-1L, profile));
        }
    }

    // ── 보너스 드롭 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rollBonusDrops")
    class BonusDrops {

        /** Bukkit Registry 없이 사용 가능한 mock ItemStack 생성 */
        private ItemStack mockItem(String materialName) {
            ItemStack stack = mock(ItemStack.class);
            Material mat = mock(Material.class);
            when(mat.name()).thenReturn(materialName);
            when(stack.getType()).thenReturn(mat);
            when(stack.getAmount()).thenReturn(1);
            when(stack.clone()).thenReturn(stack);
            return stack;
        }

        @Test
        void RELIC_0이면_항상_빈_리스트() {
            profile.setStatLevel(StatType.RELIC, 0);
            List<ItemStack> guaranteed = List.of(mockItem("RAW_IRON"));
            for (int i = 0; i < 200; i++) {
                assertTrue(service.rollBonusDrops(profile, guaranteed).isEmpty(),
                        "RELIC 0에서 보너스 드롭이 발생하면 안 됨");
            }
        }

        @Test
        void guaranteedDrops_비어있으면_빈_리스트() {
            profile.setStatLevel(StatType.RELIC, 25);
            assertTrue(service.rollBonusDrops(profile, List.of()).isEmpty());
        }

        @RepeatedTest(3)
        void RELIC_50이면_항상_보너스_드롭_발동() {
            // 50 × 0.02 = 1.0 → 100% 확률
            profile.setStatLevel(StatType.RELIC, 50);
            List<ItemStack> guaranteed = List.of(mockItem("DIAMOND"));
            for (int i = 0; i < 50; i++) {
                assertFalse(service.rollBonusDrops(profile, guaranteed).isEmpty(),
                        "RELIC 50(100%)에서 보너스 드롭 없음 (시도 " + i + ")");
            }
        }
    }
}

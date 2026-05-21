package kr.minefarm.job.jobminer.relic;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
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

import org.bukkit.Material;
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
        when(config.getRawConfig()).thenReturn(yaml);

        service = new RelicStatService(config);

        profile = new PlayerJobProfile(UUID.randomUUID());
        profile.setJobId(JobId.MINER);
    }

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
            // 기본 경험치 100, RELIC 10, 0.02/레벨 → 100 × 1.2 = 120
            profile.setStatLevel(StatType.RELIC, 10);
            long result = service.applyExpBonus(100L, profile);
            assertEquals(120L, result);
        }

        @Test
        void RELIC_25이면_50퍼센트_추가() {
            profile.setStatLevel(StatType.RELIC, 25);
            long result = service.applyExpBonus(100L, profile);
            assertEquals(150L, result);
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

    @Nested
    @DisplayName("rollBonusDrops")
    class BonusDrops {

        @Test
        void RELIC_0이면_항상_빈_리스트() {
            profile.setStatLevel(StatType.RELIC, 0);
            List<ItemStack> guaranteed = List.of(new ItemStack(Material.RAW_IRON, 1));
            for (int i = 0; i < 1000; i++) {
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
        void RELIC_50이면_확률_100퍼_이상() {
            // 50 × 0.02 = 1.0(100%) → 항상 보너스 드롭
            profile.setStatLevel(StatType.RELIC, 50);
            List<ItemStack> guaranteed = List.of(new ItemStack(Material.DIAMOND, 1));
            for (int i = 0; i < 100; i++) {
                assertFalse(service.rollBonusDrops(profile, guaranteed).isEmpty(),
                        "RELIC 50(100%)에서 보너스 드롭이 없으면 안 됨 (시도 " + i + ")");
            }
        }
    }
}

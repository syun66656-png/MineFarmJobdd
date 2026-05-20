package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.JobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceProgression")
class ExperienceProgressionTest {

    @Mock
    private JobCoreConfig config;

    private ExperienceProgression progression;

    @Nested
    @DisplayName("테이블 없이 공식만 사용할 때")
    class FormulaOnly {

        @BeforeEach
        void setUp() {
            when(config.getBaseExpRequired()).thenReturn(100L);
            when(config.getExpPerLevel()).thenReturn(Map.of());
            when(config.getMaxLevel()).thenReturn(100);
            progression = new ExperienceProgression(config);
        }

        @ParameterizedTest(name = "레벨 {0} → 필요 경험치 {1}")
        @CsvSource({"1,100", "2,200", "5,500", "10,1000", "50,5000", "100,10000"})
        void 레벨별_공식_계산(int level, long expected) {
            assertEquals(expected, progression.getRequiredForLevel(level));
        }

        @Test
        void 레벨_0_이하는_1로_처리() {
            assertEquals(100L, progression.getRequiredForLevel(0));
            assertEquals(100L, progression.getRequiredForLevel(-5));
        }
    }

    @Nested
    @DisplayName("exp-per-level 테이블이 설정된 경우")
    class WithTable {

        @BeforeEach
        void setUp() {
            when(config.getBaseExpRequired()).thenReturn(100L);
            when(config.getMaxLevel()).thenReturn(100);
            Map<Integer, Long> table = new LinkedHashMap<>();
            table.put(1, 50L);
            table.put(2, 150L);
            table.put(5, 500L);
            table.put(10, 2000L);
            when(config.getExpPerLevel()).thenReturn(table);
            progression = new ExperienceProgression(config);
        }

        @Test
        void 테이블에_있는_레벨은_테이블값_반환() {
            assertEquals(50L,   progression.getRequiredForLevel(1));
            assertEquals(150L,  progression.getRequiredForLevel(2));
            assertEquals(500L,  progression.getRequiredForLevel(5));
            assertEquals(2000L, progression.getRequiredForLevel(10));
        }

        @Test
        void 테이블에_없는_레벨은_공식으로_폴백() {
            // 레벨 3 → 테이블 없음 → 100 × 3 = 300
            assertEquals(300L, progression.getRequiredForLevel(3));
            // 레벨 7 → 테이블 없음 → 100 × 7 = 700
            assertEquals(700L, progression.getRequiredForLevel(7));
        }
    }

    @Nested
    @DisplayName("PlayerJobProfile 연동")
    class WithProfile {

        private PlayerJobProfile profile;

        @BeforeEach
        void setUp() {
            when(config.getBaseExpRequired()).thenReturn(100L);
            when(config.getExpPerLevel()).thenReturn(Map.of(1, 200L, 2, 400L));
            when(config.getMaxLevel()).thenReturn(100);
            progression = new ExperienceProgression(config);

            profile = new PlayerJobProfile(UUID.randomUUID());
            profile.setLevel(1);
            profile.setExperience(80L);
        }

        @Test
        void getRequiredForNextLevel_현재레벨기준() {
            assertEquals(200L, progression.getRequiredForNextLevel(profile));
        }

        @Test
        void getExpUntilNextLevel_남은경험치_정확() {
            // 200 - 80 = 120
            assertEquals(120L, progression.getExpUntilNextLevel(profile));
        }

        @Test
        void 경험치가_최댓값_초과해도_0이상() {
            profile.setExperience(9999L);
            assertTrue(progression.getExpUntilNextLevel(profile) >= 0L);
        }
    }

    @Nested
    @DisplayName("applyConfig — 리로드 반영")
    class Reload {

        @Test
        void 리로드_후_새_테이블_즉시_반영() {
            when(config.getBaseExpRequired()).thenReturn(100L);
            when(config.getExpPerLevel()).thenReturn(Map.of());
            when(config.getMaxLevel()).thenReturn(100);
            progression = new ExperienceProgression(config);

            // 기존: 공식 → 레벨 1 = 100
            assertEquals(100L, progression.getRequiredForLevel(1));

            // 설정 변경 후 applyConfig
            JobCoreConfig newConfig = mock(JobCoreConfig.class);
            when(newConfig.getBaseExpRequired()).thenReturn(100L);
            when(newConfig.getExpPerLevel()).thenReturn(Map.of(1, 999L));
            when(newConfig.getMaxLevel()).thenReturn(100);
            progression.applyConfig(newConfig);

            // 새 값 반영
            assertEquals(999L, progression.getRequiredForLevel(1));
        }
    }
}

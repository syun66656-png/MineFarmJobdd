package kr.minefarm.job.jobcore.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobId / StatType")
class DomainEnumTest {

    @Nested
    @DisplayName("JobId.fromKey")
    class JobIdFromKey {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({"none,NONE", "miner,MINER", "farmer,FARMER", "hunter,HUNTER",
                    "MINER,MINER", "Miner,MINER", "  miner  ,MINER"})
        void 정상_키_파싱(String key, String expected) {
            var result = JobId.fromKey(key.trim());
            assertTrue(result.isPresent());
            assertEquals(JobId.valueOf(expected), result.get());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void null_또는_빈문자열은_NONE(String key) {
            var result = JobId.fromKey(key);
            assertTrue(result.isPresent());
            assertEquals(JobId.NONE, result.get());
        }

        @ParameterizedTest
        @ValueSource(strings = {"blacksmith", "witch", "???", "광부"})
        void 알수없는_키는_empty(String key) {
            assertTrue(JobId.fromKey(key).isEmpty());
        }

        @Test
        void NONE은_hasJob_false() {
            assertFalse(JobId.NONE.hasJob());
        }

        @Test
        void 나머지_직업은_hasJob_true() {
            assertTrue(JobId.MINER.hasJob());
            assertTrue(JobId.FARMER.hasJob());
            assertTrue(JobId.HUNTER.hasJob());
        }
    }

    @Nested
    @DisplayName("StatType.fromKey")
    class StatTypeFromKey {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({"relic,RELIC", "skill,SKILL", "sell,SELL", "auto_sell,AUTO_SELL",
                    "RELIC,RELIC", "Skill,SKILL", "SELL,SELL", "AUTO_SELL,AUTO_SELL"})
        void 정상_키_파싱(String key, String expected) {
            var result = StatType.fromKey(key);
            assertTrue(result.isPresent(), "키를 파싱하지 못함: " + key);
            assertEquals(StatType.valueOf(expected), result.get());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void null_또는_빈문자열은_empty(String key) {
            assertTrue(StatType.fromKey(key).isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"strength", "agility", "unknown"})
        void 알수없는_키는_empty(String key) {
            assertTrue(StatType.fromKey(key).isEmpty());
        }

        @Test
        void 모든_StatType_getKey_역방향_파싱() {
            for (StatType type : StatType.values()) {
                var parsed = StatType.fromKey(type.getKey());
                assertTrue(parsed.isPresent());
                assertEquals(type, parsed.get());
            }
        }
    }
}

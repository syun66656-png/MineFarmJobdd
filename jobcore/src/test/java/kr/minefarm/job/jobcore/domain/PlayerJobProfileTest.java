package kr.minefarm.job.jobcore.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerJobProfile")
class PlayerJobProfileTest {

    private UUID uuid;
    private PlayerJobProfile profile;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        profile = new PlayerJobProfile(uuid);
    }

    @Nested
    @DisplayName("초기 상태")
    class InitialState {

        @Test
        void UUID_보존() {
            assertEquals(uuid, profile.getUuid());
        }

        @Test
        void 직업_없음으로_초기화() {
            assertEquals(JobId.NONE, profile.getJobId());
        }

        @Test
        void 레벨_1로_초기화() {
            assertEquals(1, profile.getLevel());
        }

        @Test
        void 경험치_0으로_초기화() {
            assertEquals(0L, profile.getExperience());
        }

        @Test
        void 스탯포인트_0으로_초기화() {
            assertEquals(0, profile.getStatPoints());
        }

        @Test
        void 자동판매_OFF로_초기화() {
            assertFalse(profile.isAutoSellEnabled());
        }

        @Test
        void dirty_false로_초기화() {
            assertFalse(profile.isDirty());
        }
    }

    @Nested
    @DisplayName("setter → dirty 자동 마킹")
    class DirtyTracking {

        @Test
        void setJobId_호출_시_dirty() {
            profile.setJobId(JobId.MINER);
            assertTrue(profile.isDirty());
        }

        @Test
        void setLevel_호출_시_dirty() {
            profile.setLevel(5);
            assertTrue(profile.isDirty());
        }

        @Test
        void setExperience_호출_시_dirty() {
            profile.setExperience(100L);
            assertTrue(profile.isDirty());
        }

        @Test
        void setStatPoints_호출_시_dirty() {
            profile.setStatPoints(3);
            assertTrue(profile.isDirty());
        }

        @Test
        void setAutoSellEnabled_호출_시_dirty() {
            profile.setAutoSellEnabled(true);
            assertTrue(profile.isDirty());
        }

        @Test
        void clearDirty_후_false() {
            profile.setLevel(2);
            profile.clearDirty();
            assertFalse(profile.isDirty());
        }
    }

    @Nested
    @DisplayName("음수·범위 방어")
    class Bounds {

        @Test
        void setLevel_0이하_1로_클램핑() {
            profile.setLevel(0);
            assertEquals(1, profile.getLevel());
            profile.setLevel(-10);
            assertEquals(1, profile.getLevel());
        }

        @Test
        void setExperience_음수_0으로_클램핑() {
            profile.setExperience(-500L);
            assertEquals(0L, profile.getExperience());
        }

        @Test
        void setStatPoints_음수_0으로_클램핑() {
            profile.setStatPoints(-1);
            assertEquals(0, profile.getStatPoints());
        }

        @Test
        void setStatLevel_음수_0으로_클램핑() {
            profile.setStatLevel(StatType.SELL, -3);
            assertEquals(0, profile.getStatLevel(StatType.SELL));
        }
    }

    @Nested
    @DisplayName("Snapshot")
    class Snapshots {

        @Test
        void snapshot은_현재_값을_정확히_캡처() {
            profile.setJobId(JobId.MINER);
            profile.setLevel(7);
            profile.setExperience(1234L);
            profile.setStatPoints(3);
            profile.setStatLevel(StatType.SELL, 5);
            profile.setAutoSellEnabled(true);

            PlayerJobProfile.Snapshot snap = profile.snapshot();

            assertEquals(JobId.MINER, snap.jobId());
            assertEquals(7, snap.level());
            assertEquals(1234L, snap.experience());
            assertEquals(3, snap.statPoints());
            assertEquals(5, snap.statSell());
            assertTrue(snap.autoSellEnabled());
        }

        @Test
        void snapshot_호출_후_dirty_해제됨() {
            profile.setLevel(10);
            assertTrue(profile.isDirty());

            profile.snapshot();
            assertFalse(profile.isDirty());  // snapshot()이 clearDirty() 포함
        }

        @Test
        void snapshot_후_수정해도_스냅샷_값_불변() {
            profile.setLevel(5);
            PlayerJobProfile.Snapshot snap = profile.snapshot();

            profile.setLevel(99);  // 스냅샷 이후 수정

            assertEquals(5, snap.level());  // 스냅샷은 변하지 않음
            assertEquals(99, profile.getLevel());
        }
    }

    @Nested
    @DisplayName("동시성 — 멀티스레드 수정")
    class Concurrency {

        @Test
        void 동시_스탯포인트_증가_손실없음() throws InterruptedException {
            int threads = 20;
            int perThread = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            synchronized (profile) {
                                profile.setStatPoints(profile.getStatPoints() + 1);
                            }
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();

            assertEquals(threads * perThread, profile.getStatPoints());
        }

        @Test
        void snapshot과_setter_동시_실행시_일관성() throws InterruptedException {
            profile.setLevel(1);
            profile.setExperience(0L);

            int writers = 10;
            ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(writers + 1);
            List<PlayerJobProfile.Snapshot> snapshots = new java.util.concurrent.CopyOnWriteArrayList<>();

            // 스냅샷 스레드
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        snapshots.add(profile.snapshot());
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });

            // writer 스레드들
            for (int i = 0; i < writers; i++) {
                final int val = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        profile.setLevel(val + 1);
                        profile.setExperience((long) val * 100);
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();

            // 각 스냅샷의 level은 1 이상 (음수나 0이 없어야 함)
            for (PlayerJobProfile.Snapshot snap : snapshots) {
                assertTrue(snap.level() >= 1, "스냅샷 level이 음수: " + snap.level());
            }
        }
    }
}

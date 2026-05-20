package kr.minefarm.job.jobcore.api;

import java.util.function.Supplier;

/**
 * 직업 플러그인이 바닐라 경험치(구슬·드롭)를 의도적으로 허용해야 할 때 사용.
 * {@link #runAllowingVanilla(Runnable)} 블록 안에서는 {@link kr.minefarm.job.jobcore.listener.ExperienceListener} 가 차단하지 않는다.
 * <p>
 * 일반 직업 EXP 지급은 {@link kr.minefarm.job.jobcore.service.JobExperienceService} 를 사용하면
 * 바닐라 구슬을 거치지 않으므로 바이패스가 필요 없다.
 */
public final class ExperienceBypass {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ExperienceBypass() {
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void runAllowingVanilla(Runnable action) {
        enter();
        try {
            action.run();
        } finally {
            leave();
        }
    }

    public static <T> T callAllowingVanilla(Supplier<T> supplier) {
        enter();
        try {
            return supplier.get();
        } finally {
            leave();
        }
    }

    private static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    private static void leave() {
        int next = DEPTH.get() - 1;
        if (next <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(next);
        }
    }
}

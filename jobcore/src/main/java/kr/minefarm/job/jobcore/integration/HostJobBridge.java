package kr.minefarm.job.jobcore.integration;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 호스트 ClassLoader 의 {@link Job} 구현을 JobCore 쪽 {@link Job} 으로 연결.
 * <p>
 * {@code refreshPassiveEffects}, {@code isEnabled} 를 포함한 모든 메서드를 브리지한다.
 */
public final class HostJobBridge implements Job {

    private final Object delegate;

    public HostJobBridge(Object delegate) {
        this.delegate = delegate;
    }

    @Override
    public JobId getId() {
        Object value = invoke("getId");
        if (value instanceof JobId jobId) {
            return JobId.fromKey(jobId.getKey()).orElse(JobId.NONE);
        }
        if (value instanceof Enum<?> enumValue) {
            return JobId.fromKey(enumValue.name().toLowerCase(Locale.ROOT)).orElse(JobId.NONE);
        }
        return JobId.NONE;
    }

    @Override
    public String getDisplayName() {
        Object value = invoke("getDisplayName");
        return value != null ? value.toString() : "";
    }

    @Override
    public void onSelect(Player player) {
        invokeVoid("onSelect", new Class<?>[]{Player.class}, new Object[]{player});
    }

    @Override
    public void onDeselect(Player player) {
        invokeVoid("onDeselect", new Class<?>[]{Player.class}, new Object[]{player});
    }

    /**
     * 패시브 갱신 브리지.
     * 기존에 오버라이드가 없어 default no-op 이 호출되던 문제를 수정한다.
     */
    @Override
    public void refreshPassiveEffects(Player player, PlayerJobProfile profile) {
        invokeVoid("refreshPassiveEffects",
                new Class<?>[]{Player.class, PlayerJobProfile.class},
                new Object[]{player, profile});
    }

    @Override
    public boolean isEnabled() {
        Object value = invoke("isEnabled");
        return !(value instanceof Boolean b) || b;
    }

    // ── reflection 헬퍼 ──────────────────────────────────────────────────────

    private Object invoke(String methodName) {
        try {
            Method method = delegate.getClass().getMethod(methodName);
            return method.invoke(delegate);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Host job missing method: " + methodName, exception);
        }
    }

    private void invokeVoid(String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method method = delegate.getClass().getMethod(methodName, paramTypes);
            method.invoke(delegate, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Host job call failed: " + methodName, exception);
        }
    }
}

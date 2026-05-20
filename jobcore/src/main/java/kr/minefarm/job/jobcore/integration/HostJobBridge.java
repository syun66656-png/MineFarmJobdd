package kr.minefarm.job.jobcore.integration;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.domain.JobId;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 호스트 ClassLoader 의 {@link Job} 구현을 JobCore 쪽 {@link Job} 으로 연결.
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
            return JobId.fromKey(enumValue.name().toLowerCase(java.util.Locale.ROOT)).orElse(JobId.NONE);
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
        invoke("onSelect", Player.class, player);
    }

    @Override
    public void onDeselect(Player player) {
        invoke("onDeselect", Player.class, player);
    }

    private Object invoke(String methodName) {
        try {
            Method method = delegate.getClass().getMethod(methodName);
            return method.invoke(delegate);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Host job missing method: " + methodName, exception);
        }
    }

    private void invoke(String methodName, Class<?> paramType, Object argument) {
        try {
            Method method = delegate.getClass().getMethod(methodName, paramType);
            method.invoke(delegate, argument);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Host job call failed: " + methodName, exception);
        }
    }
}

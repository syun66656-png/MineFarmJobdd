package kr.minefarm.job.jobcore.integration;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.api.JobContext;
import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.api.JobModule;
import kr.minefarm.job.jobcore.bootstrap.JobCoreContextImpl;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * 다른 플러그인 ClassLoader 에 로드된 {@link JobModule} 구현체를 JobCore 에서 호출하기 위한 브리지.
 */
public final class HostJobModuleBridge implements JobModule {

    private final Object delegate;
    private final JavaPlugin hostPlugin;
    private final JobCoreAPI coreApi;

    public HostJobModuleBridge(Object delegate, JavaPlugin hostPlugin, JobCoreAPI coreApi) {
        this.delegate = delegate;
        this.hostPlugin = hostPlugin;
        this.coreApi = coreApi;
    }

    public static HostJobModuleBridge load(JavaPlugin hostPlugin, String moduleClassName, JobCoreAPI coreApi)
            throws ReflectiveOperationException {
        ClassLoader loader = hostPlugin.getClass().getClassLoader();
        Class<?> moduleClass = Class.forName(moduleClassName, true, loader);
        Object instance = moduleClass.getDeclaredConstructor().newInstance();
        return new HostJobModuleBridge(instance, hostPlugin, coreApi);
    }

    @Override
    public String getModuleId() {
        return invokeString("getModuleId");
    }

    @Override
    public Collection<Job> getJobs() {
        try {
            Method method = delegate.getClass().getMethod("getJobs");
            Object result = method.invoke(delegate);
            Iterable<?> source;
            if (result instanceof Collection<?> collection) {
                source = collection;
            } else if (result instanceof List<?> list) {
                source = list;
            } else {
                return List.of();
            }
            List<Job> jobs = new ArrayList<>();
            for (Object job : source) {
                jobs.add(new HostJobBridge(job));
            }
            return jobs;
        } catch (ReflectiveOperationException exception) {
            hostPlugin.getLogger().log(Level.SEVERE, "[JobCore] Failed to read jobs from host module.", exception);
            return List.of();
        }
    }

    @Override
    public void onEnable(JobContext context) {
        JobContext hostContext = new JobCoreContextImpl(hostPlugin, coreApi);
        invoke("onEnable", JobContext.class, hostContext);
    }

    @Override
    public void onDisable() {
        invoke("onDisable");
    }

    private String invokeString(String methodName) {
        try {
            Method method = delegate.getClass().getMethod(methodName);
            Object value = method.invoke(delegate);
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Host module missing method: " + methodName, exception);
        }
    }

    private void invoke(String methodName, Class<?> paramType, Object argument) {
        try {
            Method method = delegate.getClass().getMethod(methodName, paramType);
            method.invoke(delegate, argument);
        } catch (ReflectiveOperationException exception) {
            hostPlugin.getLogger().log(Level.SEVERE,
                    "[JobCore] Host module call failed: " + methodName, exception);
        }
    }

    private void invoke(String methodName) {
        try {
            Method method = delegate.getClass().getMethod(methodName);
            method.invoke(delegate);
        } catch (ReflectiveOperationException exception) {
            hostPlugin.getLogger().log(Level.SEVERE,
                    "[JobCore] Host module call failed: " + methodName, exception);
        }
    }
}

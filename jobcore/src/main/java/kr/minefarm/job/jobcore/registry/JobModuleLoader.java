package kr.minefarm.job.jobcore.registry;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.api.JobModule;
import kr.minefarm.job.jobcore.bootstrap.JobCoreContextImpl;
import kr.minefarm.job.jobcore.bootstrap.ModuleReloadResult;
import kr.minefarm.job.jobcore.bootstrap.PendingJobModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 직업 모듈 등록·활성화·비활성화·리로드.
 */
public final class JobModuleLoader {

    public static final String CORE_ID = "core";

    private final JavaPlugin corePlugin;
    private final JobRegistry jobRegistry;
    private final Map<String, PendingJobModule> modules = new LinkedHashMap<>();
    private final Map<String, JobCoreContextImpl> moduleContexts = new LinkedHashMap<>();

    public JobModuleLoader(JavaPlugin corePlugin, JobRegistry jobRegistry) {
        this.corePlugin = corePlugin;
        this.jobRegistry = jobRegistry;
    }

    public void register(JobModule module, JavaPlugin hostPlugin) {
        if (modules.containsKey(module.getModuleId())) {
            throw new IllegalStateException("Module already registered: " + module.getModuleId());
        }
        modules.put(module.getModuleId(), new PendingJobModule(module, hostPlugin));
    }

    public Optional<String> findModuleId(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase();
        if (CORE_ID.equals(normalized) || "jobcore".equals(normalized)) {
            return Optional.of(CORE_ID);
        }
        if ("all".equals(normalized)) {
            return Optional.of("all");
        }
        for (String moduleId : modules.keySet()) {
            if (moduleId.equalsIgnoreCase(normalized)) {
                return Optional.of(moduleId);
            }
        }
        return Optional.empty();
    }

    public List<String> getModuleIds() {
        return List.copyOf(modules.keySet());
    }

    public void enableAll(JobCoreAPI coreApi) {
        for (PendingJobModule pending : modules.values()) {
            try {
                enableModule(pending, coreApi);
            } catch (Exception exception) {
                pending.hostPlugin().getLogger().log(Level.SEVERE,
                        "Failed to enable job module: " + pending.module().getModuleId(), exception);
            }
        }
    }

    public void disableAll() {
        List<PendingJobModule> reversed = new ArrayList<>(modules.values());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            try {
                disableModule(reversed.get(i));
            } catch (Exception exception) {
                PendingJobModule pending = reversed.get(i);
                pending.hostPlugin().getLogger().log(Level.SEVERE,
                        "Failed to disable job module: " + pending.module().getModuleId(), exception);
            }
        }
    }

    /**
     * 단일 직업 모듈 리로드: onDisable → onEnable (설정은 onEnable에서 재로드).
     */
    public ModuleReloadResult reloadModule(String moduleId, JobCoreAPI coreApi) {
        PendingJobModule pending = modules.get(moduleId);
        if (pending == null) {
            return ModuleReloadResult.fail(moduleId, "등록되지 않은 모듈입니다.", null);
        }
        try {
            disableModule(pending);
            enableModule(pending, coreApi);
            return ModuleReloadResult.ok(moduleId);
        } catch (Exception exception) {
            corePlugin.getLogger().log(Level.SEVERE,
                    "[JobCore] Failed to reload module: " + moduleId, exception);
            return ModuleReloadResult.fail(moduleId, exception.getMessage(), exception);
        }
    }

    /**
     * 직업 모듈 전부 리로드 (Core 제외).
     * 언로드: 등록 역순 → 로드: 등록 순서.
     */
    public List<ModuleReloadResult> reloadAllJobModules(JobCoreAPI coreApi) {
        List<ModuleReloadResult> results = new ArrayList<>();
        List<PendingJobModule> ordered = new ArrayList<>(modules.values());

        for (int i = ordered.size() - 1; i >= 0; i--) {
            PendingJobModule pending = ordered.get(i);
            try {
                disableModule(pending);
            } catch (Exception exception) {
                results.add(ModuleReloadResult.fail(
                        pending.module().getModuleId(), exception.getMessage(), exception));
            }
        }

        for (PendingJobModule pending : ordered) {
            String moduleId = pending.module().getModuleId();
            if (results.stream().anyMatch(result ->
                    result instanceof ModuleReloadResult.Failure failure
                            && failure.targetId().equals(moduleId))) {
                continue;
            }
            try {
                enableModule(pending, coreApi);
                results.add(ModuleReloadResult.ok(moduleId));
            } catch (Exception exception) {
                corePlugin.getLogger().log(Level.SEVERE,
                        "[JobCore] Failed to enable module after reload: " + moduleId, exception);
                results.add(ModuleReloadResult.fail(moduleId, exception.getMessage(), exception));
            }
        }
        return results;
    }

    private void enableModule(PendingJobModule pending, JobCoreAPI coreApi) {
        JobModule module = pending.module();
        JavaPlugin hostPlugin = pending.hostPlugin();
        String moduleId = module.getModuleId();

        JobCoreContextImpl context = new JobCoreContextImpl(hostPlugin, coreApi);
        moduleContexts.put(moduleId, context);

        for (Job job : module.getJobs()) {
            jobRegistry.register(job);
        }
        module.onEnable(context);
        hostPlugin.getLogger().info("Job module enabled: " + moduleId);
    }

    private void disableModule(PendingJobModule pending) {
        JobModule module = pending.module();
        JavaPlugin hostPlugin = pending.hostPlugin();
        String moduleId = module.getModuleId();

        JobCoreContextImpl context = moduleContexts.remove(moduleId);
        if (context != null) {
            context.unregisterListeners();
        }

        module.onDisable();

        for (Job job : module.getJobs()) {
            jobRegistry.unregister(job.getId());
        }
        hostPlugin.getLogger().info("Job module disabled: " + moduleId);
    }
}

package kr.minefarm.job.jobcore.bootstrap;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.api.JobModule;
import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.api.DatabaseManager;
import kr.minefarm.job.jobcore.api.PlayerJobRepository;
import kr.minefarm.job.jobcore.database.async.AsyncSaveService;
import kr.minefarm.job.jobcore.database.migration.SchemaMigration;
import kr.minefarm.job.jobcore.service.JdbcPlayerJobRepository;
import kr.minefarm.job.jobcore.integration.PluginHooks;
import kr.minefarm.job.jobcore.integration.velocity.VelocitySupport;
import kr.minefarm.job.jobcore.command.JobCommand;
import kr.minefarm.job.jobcore.api.PaperCommandRegistration;
import kr.minefarm.job.jobcore.command.StatCommand;
import kr.minefarm.job.jobcore.command.admin.JobAdminCommand;
import kr.minefarm.job.jobcore.config.GuiConfig;
import kr.minefarm.job.jobcore.config.MessageConfig;
import kr.minefarm.job.jobcore.gui.GuiManager;
import kr.minefarm.job.jobcore.listener.ExperienceListener;
import kr.minefarm.job.jobcore.listener.GuiListener;
import kr.minefarm.job.jobcore.listener.PlayerConnectionListener;
import kr.minefarm.job.jobcore.registry.JobModuleLoader;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import kr.minefarm.job.jobcore.service.RankingService;
import kr.minefarm.job.jobcore.service.StatService;
import kr.minefarm.job.jobcore.service.JobGuiService;
import kr.minefarm.job.jobcore.util.PlaceholderResolver;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.ExperienceProgression;
import kr.minefarm.job.jobcore.service.JobExperienceService;
import kr.minefarm.job.jobcore.service.JobService;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * JobCore 생명주기 관리.
 * <pre>
 *   start() → registerModule() × N → loadJobModules()
 *   shutdown()
 * </pre>
 */
public final class JobCoreBootstrap {

    private final JavaPlugin plugin;
    private final List<PendingJobModule> pendingModules = new ArrayList<>();

    private JobCoreState state = JobCoreState.CREATED;
    private JobCoreConfig config;
    private DatabaseManager databaseManager;
    private PlayerJobRepository playerJobRepository;
    private JobRegistry jobRegistry;
    private PlayerProfileService profileService;
    private ExperienceProgression experienceProgression;
    private JobService jobService;
    private JobExperienceService jobExperienceService;
    private StatService statService;
    private JobCoreAPI coreApi;
    private JobModuleLoader moduleLoader;
    private AsyncSaveService asyncSaveService;
    private PluginHooks pluginHooks;
    private MessageConfig messageConfig;
    private GuiConfig guiConfig;
    private GuiManager guiManager;
    private RankingService rankingService;
    private PlaceholderResolver placeholderResolver;
    private JobGuiService jobGuiService;
    private ExperienceListener experienceListener;
    private GuiListener guiListener;
    private VelocitySupport velocitySupport;
    private boolean modulesLoaded;

    public JobCoreBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return 성공 시 true, DB 초기화 실패 시 false
     */
    public boolean start() {
        try {
            loadConfiguration();
            initializeVelocitySupport();
            initializeDatabase();
            initializeServices();
            initializeIntegrations();
            startBackgroundTasks();
            state = JobCoreState.INTEGRATIONS_READY;
            plugin.getLogger().info("JobCore started (state=" + state + "). Register modules, then call loadJobModules().");
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "JobCore failed to start.", exception);
            shutdown();
            return false;
        }
    }

    /** 플러그인 onEnable 단계에서 모듈 인스턴스를 등록만 한다 (아직 enable 안 함). */
    public void registerModule(JobModule module, org.bukkit.plugin.java.JavaPlugin hostPlugin) {
        if (state == JobCoreState.SHUTDOWN) {
            throw new IllegalStateException("JobCore is shut down");
        }
        if (modulesLoaded) {
            throw new IllegalStateException("Cannot register modules after loadJobModules()");
        }
        if (state == JobCoreState.CREATED) {
            throw new IllegalStateException("Call start() before registerModule()");
        }
        for (PendingJobModule existing : pendingModules) {
            if (existing.module().getModuleId().equals(module.getModuleId())) {
                throw new IllegalStateException("Duplicate module id: " + module.getModuleId());
            }
        }
        PendingJobModule pending = new PendingJobModule(module, hostPlugin);
        pendingModules.add(pending);
        if (moduleLoader != null) {
            moduleLoader.register(module, hostPlugin);
        }
    }

    /** 등록된 모든 {@link JobModule}을 활성화한다. */
    public void loadJobModules() {
        if (state != JobCoreState.INTEGRATIONS_READY) {
            throw new IllegalStateException("Call start() before loadJobModules()");
        }
        if (modulesLoaded) {
            return;
        }
        moduleLoader.enableAll(coreApi);
        modulesLoaded = true;
        state = JobCoreState.RUNNING;
        plugin.getLogger().info("Loaded " + pendingModules.size() + " job module(s).");
    }

    public void shutdown() {
        if (state == JobCoreState.SHUTDOWN) {
            return;
        }

        if (moduleLoader != null && modulesLoaded) {
            moduleLoader.disableAll();
            modulesLoaded = false;
        }
        shutdownIntegrations();
        if (rankingService != null) {
            rankingService.shutdown();
        }
        if (asyncSaveService != null && config != null) {
            asyncSaveService.shutdownAndFlush(config.getAsyncShutdownTimeoutSeconds());
        }
        if (databaseManager != null) {
            databaseManager.close();
        }

        state = JobCoreState.SHUTDOWN;
        plugin.getLogger().info("JobCore shut down.");
    }

    private void loadConfiguration() {
        plugin.saveDefaultConfig();
        config = new JobCoreConfig(plugin.getConfig());
        messageConfig = new MessageConfig(plugin);
        guiConfig = new GuiConfig(plugin);
        state = JobCoreState.CONFIG_LOADED;
        plugin.getLogger().info("[JobCore] Configuration loaded.");
    }

    private void initializeDatabase() throws SQLException {
        databaseManager = new HikariDatabaseManager(plugin);
        databaseManager.connect(config.getDatabase());

        playerJobRepository = new JdbcPlayerJobRepository(databaseManager);
        new SchemaMigration(databaseManager, plugin.getLogger()).migrate();

        state = JobCoreState.DATABASE_READY;
        plugin.getLogger().info("[JobCore] Database ready.");
    }

    private void initializeServices() {
        jobRegistry = new JobRegistry();
        profileService = new PlayerProfileService(playerJobRepository);
        experienceProgression = new ExperienceProgression(config);
        jobService = new JobService(plugin, jobRegistry, profileService, config);
        jobExperienceService = new JobExperienceService(plugin, profileService, jobRegistry);
        statService = new StatService(plugin, profileService, jobRegistry, config);
        rankingService = new RankingService(plugin, databaseManager);
        guiManager = new GuiManager();
        coreApi = new JobCoreAPIImpl(
                databaseManager,
                jobRegistry,
                profileService,
                jobService,
                jobExperienceService,
                rankingService,
                statService,
                velocitySupport.getProvider()
        );
        moduleLoader = new JobModuleLoader(plugin, jobRegistry);

        for (PendingJobModule pending : pendingModules) {
            moduleLoader.register(pending.module(), pending.hostPlugin());
        }

        state = JobCoreState.SERVICES_READY;
        plugin.getLogger().info("[JobCore] Services ready.");
    }

    private void initializeIntegrations() {
        pluginHooks = new PluginHooks(plugin);
        pluginHooks.detect();

        placeholderResolver = new PlaceholderResolver(
                pluginHooks,
                experienceProgression,
                plugin.getLogger()
        );
        jobGuiService = new JobGuiService(
                plugin,
                guiConfig,
                messageConfig,
                guiManager,
                profileService,
                jobService,
                jobRegistry,
                rankingService,
                placeholderResolver,
                statService
        );

        registerCommands();
        guiListener = new GuiListener(guiManager);
        experienceListener = new ExperienceListener(config.isBlockVanillaExpOrbs());
        plugin.getServer().getPluginManager().registerEvents(guiListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(plugin, profileService, jobRegistry),
                plugin
        );
        plugin.getServer().getPluginManager().registerEvents(experienceListener, plugin);

        pluginHooks.registerJobCorePlaceholders(
                plugin.getPluginMeta().getVersion(),
                profileService,
                jobRegistry,
                experienceProgression,
                rankingService
        );

        state = JobCoreState.INTEGRATIONS_READY;
        plugin.getLogger().info("[JobCore] Integrations ready.");
    }

    private void registerCommands() {
        PaperCommandRegistration.register(
                plugin,
                "직업",
                "직업 선택 및 개인 정보 GUI",
                new JobCommand(jobGuiService, messageConfig)
        );

        JobAdminCommand adminCommands = new JobAdminCommand(jobGuiService, messageConfig, this);
        PaperCommandRegistration.register(
                plugin,
                "직업설정",
                "관리자 유저 데이터 관리 GUI",
                adminCommands,
                null,
                "minefarmjob.admin"
        );
        PaperCommandRegistration.register(
                plugin,
                "직업리로드",
                "JobCore·직업 모듈 설정 리로드",
                adminCommands,
                adminCommands,
                "minefarmjob.admin"
        );

        PaperCommandRegistration.register(
                plugin,
                "스탯",
                "스탯 포인트 투자 및 광부 자동판매 토글",
                new StatCommand(jobGuiService, messageConfig)
        );

        plugin.getLogger().info("[JobCore] Commands registered (Paper registerCommand).");
    }

    private void startBackgroundTasks() {
        asyncSaveService = new AsyncSaveService(
                plugin,
                profileService,
                playerJobRepository,
                config.getAsyncFlushIntervalTicks()
        );
        asyncSaveService.start();
        rankingService.start();
    }

    private void initializeVelocitySupport() {
        velocitySupport = new VelocitySupport(plugin);
        velocitySupport.start(config.isVelocitySupport());
    }

    private void shutdownIntegrations() {
        if (velocitySupport != null) {
            velocitySupport.shutdown();
        }
        if (pluginHooks != null) {
            pluginHooks.unregisterPlaceholders();
        }
    }

    public PluginHooks getPluginHooks() {
        return pluginHooks;
    }

    public JobCoreState getState() {
        return state;
    }

    public JobCoreAPI getApi() {
        return coreApi;
    }

    public JobModuleLoader getModuleLoader() {
        return moduleLoader;
    }

    public boolean isModulesLoaded() {
        return modulesLoaded;
    }

    public JobCoreConfig getConfig() {
        return config;
    }

    public JobGuiService getJobGuiService() {
        return jobGuiService;
    }

    public void sendReloadStart(CommandSender sender) {
        sender.sendMessage("§e[JobCore] §f리로드 시작...");
    }

    public void sendReloadComplete(CommandSender sender) {
        sender.sendMessage("§a[JobCore] §f리로드 완료!");
    }

    /**
     * JobCore 설정(config.yml, gui.yml, messages.yml)만 리로드한다. DB·캐시는 유지한다.
     */
    public ModuleReloadResult reloadCore() {
        try {
            plugin.reloadConfig();
            config = new JobCoreConfig(plugin.getConfig());
            messageConfig.reload(plugin);
            guiConfig.reload(plugin);

            experienceProgression.applyConfig(config);
            jobService = new JobService(plugin, jobRegistry, profileService, config);
            statService = new StatService(plugin, profileService, jobRegistry, config);

            if (velocitySupport != null) {
                velocitySupport.start(config.isVelocitySupport());
            }
            coreApi = new JobCoreAPIImpl(
                    databaseManager,
                    jobRegistry,
                    profileService,
                    jobService,
                    jobExperienceService,
                    rankingService,
                    statService,
                    velocitySupport.getProvider()
            );

            placeholderResolver = new PlaceholderResolver(
                    pluginHooks,
                    experienceProgression,
                    plugin.getLogger()
            );
            jobGuiService = new JobGuiService(
                    plugin,
                    guiConfig,
                    messageConfig,
                    guiManager,
                    profileService,
                    jobService,
                    jobRegistry,
                    rankingService,
                    placeholderResolver,
                    statService
            );
            refreshCommandExecutors();

            if (experienceListener != null) {
                HandlerList.unregisterAll(experienceListener);
            }
            experienceListener = new ExperienceListener(config.isBlockVanillaExpOrbs());
            plugin.getServer().getPluginManager().registerEvents(experienceListener, plugin);

            pluginHooks.unregisterPlaceholders();
            pluginHooks.registerJobCorePlaceholders(
                    plugin.getPluginMeta().getVersion(),
                    profileService,
                    jobRegistry,
                    experienceProgression,
                    rankingService
            );

            plugin.getLogger().info("[JobCore] Core configuration reloaded.");
            return ModuleReloadResult.ok(JobModuleLoader.CORE_ID);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[JobCore] Core reload failed.", exception);
            return ModuleReloadResult.fail(
                    JobModuleLoader.CORE_ID,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    /**
     * 전체 리로드: 직업 모듈 언로드(역순) → Core → 직업 모듈 로드(순서).
     */
    public List<ModuleReloadResult> reloadAll(CommandSender sender) {
        sendReloadStart(sender);
        List<ModuleReloadResult> results = new ArrayList<>();

        if (!modulesLoaded) {
            results.add(ModuleReloadResult.fail("all", "직업 모듈이 아직 로드되지 않았습니다.", null));
            reportReloadFailures(sender, results);
            return results;
        }

        moduleLoader.disableAll();

        ModuleReloadResult coreResult = reloadCore();
        results.add(coreResult);

        if (coreResult instanceof ModuleReloadResult.Success) {
            moduleLoader.enableAll(coreApi);
            for (String moduleId : moduleLoader.getModuleIds()) {
                results.add(ModuleReloadResult.ok(moduleId));
            }
        } else {
            try {
                moduleLoader.enableAll(coreApi);
                plugin.getLogger().warning("[JobCore] Core reload failed — job modules re-enabled.");
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "[JobCore] Failed to restore job modules.", exception);
            }
        }

        reportReloadFailures(sender, results);
        if (results.stream().allMatch(result -> result instanceof ModuleReloadResult.Success)) {
            sendReloadComplete(sender);
        }
        return results;
    }

    public ModuleReloadResult reloadTarget(CommandSender sender, String targetId) {
        sendReloadStart(sender);

        ModuleReloadResult result;
        if (JobModuleLoader.CORE_ID.equals(targetId)) {
            result = reloadCore();
        } else if (!modulesLoaded) {
            result = ModuleReloadResult.fail(targetId, "직업 모듈이 아직 로드되지 않았습니다.", null);
        } else {
            result = moduleLoader.reloadModule(targetId, coreApi);
        }

        if (result instanceof ModuleReloadResult.Success) {
            sendReloadComplete(sender);
        } else if (result instanceof ModuleReloadResult.Failure failure) {
            sender.sendMessage("§c[JobCore] §f리로드 실패 (§e" + failure.targetId() + "§f): §7"
                    + failure.message());
        }
        return result;
    }

    private void refreshCommandExecutors() {
        registerCommands();
    }

    private void reportReloadFailures(CommandSender sender, List<ModuleReloadResult> results) {
        List<ModuleReloadResult.Failure> failures = results.stream()
                .filter(ModuleReloadResult.Failure.class::isInstance)
                .map(ModuleReloadResult.Failure.class::cast)
                .collect(Collectors.toList());
        for (ModuleReloadResult.Failure failure : failures) {
            sender.sendMessage("§c[JobCore] §f리로드 실패 (§e" + failure.targetId() + "§f): §7"
                    + failure.message());
        }
    }
}

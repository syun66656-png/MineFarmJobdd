package kr.minefarm.job.jobminer;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.api.JobContext;
import kr.minefarm.job.jobcore.api.JobModule;
import kr.minefarm.job.jobcore.api.PaperCommandRegistration;
import kr.minefarm.job.jobminer.autosell.AutoSellProcessor;
import kr.minefarm.job.jobminer.command.MineResetCommand;
import kr.minefarm.job.jobminer.command.RegenWandCommand;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.VaultEconomyBridge;
import kr.minefarm.job.jobminer.kit.StarterKitService;
import kr.minefarm.job.jobminer.passive.MinerPassiveEffectsService;
import kr.minefarm.job.jobminer.listener.MiningListener;
import kr.minefarm.job.jobminer.listener.RegenProtectionListener;
import kr.minefarm.job.jobminer.listener.RegenWandListener;
import kr.minefarm.job.jobminer.skill.MinerSkills;
import kr.minefarm.job.jobminer.mining.MineDropResolver;
import kr.minefarm.job.jobminer.mining.RegenMineRewardService;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.mining.RegenBlockStorage;
import kr.minefarm.job.jobminer.mining.RegenRestoreService;
import kr.minefarm.job.jobminer.shop.MineSellCalculator;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import kr.minefarm.job.jobminer.tool.RegenWandService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 광부 직업 모듈. JobCore API만 사용하며 Core 내부 패키지에 의존하지 않는다.
 */
public final class JobMinerModule implements JobModule {

    public static final String MODULE_ID = "jobminer";

    private MinerJob minerJob;

    private JobMinerConfig minerConfig;
    private RegenBlockRegistry regenBlockRegistry;
    private RegenBlockStorage regenBlockStorage;
    private RegenRestoreService regenRestoreService;
    private VaultEconomyBridge vaultEconomy;
    private MinerSkills minerSkills;

    /** reload/disable 시 해제할 커맨드 목록 */
    private final java.util.Map<String, org.bukkit.command.PluginCommand> registeredCommands
            = new java.util.LinkedHashMap<>();

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<Job> getJobs() {
        if (minerJob == null) {
            minerJob = new MinerJob();
        }
        return List.of(minerJob);
    }

    @Override
    public void onEnable(JobContext context) {
        JavaPlugin plugin = context.getPlugin();
        plugin.saveResource("jobminer/config.yml", false);
        minerConfig = new JobMinerConfig(plugin);

        StarterKitService starterKitService = new StarterKitService(minerConfig);
        MinerPassiveEffectsService passiveEffectsService = new MinerPassiveEffectsService(plugin, minerConfig);

        regenBlockStorage = new RegenBlockStorage(plugin);
        regenBlockRegistry = new RegenBlockRegistry();
        regenBlockRegistry.loadAll(regenBlockStorage.load());
        regenBlockRegistry.setOnChanged(() ->
                regenBlockStorage.save(regenBlockRegistry.getAllEntries()));
        restoreBrokenBlocksOnLoad();

        regenRestoreService = new RegenRestoreService(plugin, minerConfig, regenBlockRegistry);

        vaultEconomy = new VaultEconomyBridge(plugin);
        vaultEconomy.detect();

        PickaxeValidator pickaxeValidator = new PickaxeValidator(minerConfig);
        RegenWandService regenWandService = new RegenWandService(plugin, minerConfig);
        MineSellCalculator sellCalculator = new MineSellCalculator(minerConfig);
        AutoSellProcessor autoSellProcessor = new AutoSellProcessor(minerConfig, sellCalculator, vaultEconomy);
        MineDropResolver dropResolver = new MineDropResolver(minerConfig);
        RegenMineRewardService regenMineRewardService = new RegenMineRewardService(
                dropResolver,
                autoSellProcessor,
                regenRestoreService
        );

        minerSkills = new MinerSkills(
                plugin,
                minerConfig,
                context.getCore(),
                regenBlockRegistry,
                regenMineRewardService
        );
        minerJob.bind(context.getCore(), starterKitService, passiveEffectsService, minerSkills);

        context.registerListener(new MiningListener(
                context.getCore(),
                regenBlockRegistry,
                pickaxeValidator,
                regenMineRewardService
        ));
        context.registerListener(minerSkills);
        context.registerListener(new RegenProtectionListener(
                regenBlockRegistry,
                context.getCore(),
                pickaxeValidator
        ));
        context.registerListener(new RegenWandListener(regenWandService, regenBlockRegistry));

        registerCommand(plugin, "광산완드", "리젠 블록 관리 완드 지급",
                new RegenWandCommand(regenWandService), null, "minefarmjob.admin");
        MineResetCommand resetCommand = new MineResetCommand(regenBlockRegistry, regenRestoreService);
        registerCommand(plugin, "광산초기화", "리젠 블록 즉시 복구",
                resetCommand, resetCommand, "minefarmjob.admin");

        plugin.getLogger().info("JobMiner module ready (regen tools, " + regenBlockRegistry.size() + " blocks loaded).");
    }

    @Override
    public void onDisable() {
        // 커맨드 먼저 해제 (reload 시 중복 등록 방지)
        registeredCommands.values().forEach(PaperCommandRegistration::unregister);
        registeredCommands.clear();

        if (minerSkills != null) {
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                minerSkills.clearForPlayer(player);
            }
            minerSkills.shutdown();
            minerSkills = null;
        }
        if (minerJob != null) {
            minerJob.unbind();
        }
        if (regenRestoreService != null) {
            regenRestoreService.cancelAllPending();
        }
        if (regenBlockStorage != null && regenBlockRegistry != null) {
            regenBlockStorage.save(regenBlockRegistry.getAllEntries());
        }
        regenRestoreService = null;
        regenBlockRegistry = null;
        regenBlockStorage = null;
        vaultEconomy = null;
        minerConfig = null;
    }

    private void restoreBrokenBlocksOnLoad() {
        for (RegenBlockEntry entry : regenBlockRegistry.getAllEntries()) {
            Block block = entry.resolveBlock();
            if (block == null) {
                continue;
            }
            Material type = block.getType();
            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                entry.applyTo(block);
            }
        }
    }


    private void registerCommand(
            JavaPlugin plugin,
            String name,
            String description,
            org.bukkit.command.CommandExecutor executor,
            org.bukkit.command.TabCompleter tabCompleter,
            String permission
    ) {
        org.bukkit.command.PluginCommand cmd =
                PaperCommandRegistration.register(plugin, name, description, executor, tabCompleter, permission);
        if (cmd != null) {
            registeredCommands.put(name, cmd);
        }
    }

    public JobMinerConfig getMinerConfig() {
        return minerConfig;
    }

    public RegenBlockRegistry getRegenBlockRegistry() {
        return regenBlockRegistry;
    }
}

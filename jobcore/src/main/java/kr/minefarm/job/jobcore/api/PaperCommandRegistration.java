package kr.minefarm.job.jobcore.api;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * 직업 확장 모듈(JobMiner 등)에서도 사용할 수 있는 커맨드 등록 유틸.
 * {@link JavaPlugin#getCommand} 대신 {@link PluginCommand} + {@link CommandMap} 을 사용한다.
 */
public final class PaperCommandRegistration {

    private PaperCommandRegistration() {
    }

    public static void register(
            JavaPlugin plugin,
            String name,
            String description,
            CommandExecutor executor
    ) {
        register(plugin, name, description, executor, null, null);
    }

    public static void register(
            JavaPlugin plugin,
            String name,
            String description,
            CommandExecutor executor,
            TabCompleter tabCompleter
    ) {
        register(plugin, name, description, executor, tabCompleter, null);
    }

    public static void register(
            JavaPlugin plugin,
            String name,
            String description,
            CommandExecutor executor,
            TabCompleter tabCompleter,
            String permission
    ) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(
                    String.class,
                    org.bukkit.plugin.Plugin.class
            );
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);

            command.setDescription(description != null ? description : "");
            command.setExecutor(executor);
            if (tabCompleter != null) {
                command.setTabCompleter(tabCompleter);
            }
            if (permission != null && !permission.isBlank()) {
                command.setPermission(permission);
            }

            CommandMap commandMap = resolveCommandMap();
            commandMap.register(plugin.getName(), command);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[JobCore] 커맨드 등록 실패: " + name, exception);
        }
    }

    private static CommandMap resolveCommandMap() throws ReflectiveOperationException {
        Object server = Bukkit.getServer();
        Method getCommandMap = server.getClass().getMethod("getCommandMap");
        Object map = getCommandMap.invoke(server);
        if (!(map instanceof CommandMap commandMap)) {
            throw new IllegalStateException("getCommandMap did not return CommandMap: " + map);
        }
        return commandMap;
    }
}

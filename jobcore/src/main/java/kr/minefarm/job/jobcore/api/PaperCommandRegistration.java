package kr.minefarm.job.jobcore.api;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;

/**
 * 직업 확장 모듈(JobMiner 등)에서도 사용할 수 있는 커맨드 등록 유틸.
 * <p>
 * reload 시 기존 커맨드를 먼저 해제({@link #unregister}) 한 뒤 새로 등록해야 중복을 방지할 수 있다.
 * {@link #register} 가 {@link PluginCommand} 를 반환하므로 호출자가 보관하여 reload/disable 시 전달한다.
 */
public final class PaperCommandRegistration {

    private PaperCommandRegistration() {
    }

    public static PluginCommand register(
            JavaPlugin plugin,
            String name,
            String description,
            CommandExecutor executor
    ) {
        return register(plugin, name, description, executor, null, null);
    }

    public static PluginCommand register(
            JavaPlugin plugin,
            String name,
            String description,
            CommandExecutor executor,
            TabCompleter tabCompleter
    ) {
        return register(plugin, name, description, executor, tabCompleter, null);
    }

    public static PluginCommand register(
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
            return command;

        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[JobCore] 커맨드 등록 실패: " + name, exception);
            return null;
        }
    }

    /**
     * 등록된 커맨드를 CommandMap 에서 제거한다. reload 직전에 호출해야 한다.
     *
     * @param command unregister 할 PluginCommand (null 이면 아무것도 안 함)
     */
    public static void unregister(PluginCommand command) {
        if (command == null) {
            return;
        }
        try {
            CommandMap commandMap = resolveCommandMap();
            command.unregister(commandMap);
            // knownCommands 에서도 명시적으로 제거
            Map<String, org.bukkit.command.Command> knownCommands = resolveKnownCommands(commandMap);
            if (knownCommands != null) {
                knownCommands.remove(command.getName());
                knownCommands.remove(command.getName().toLowerCase(java.util.Locale.ROOT));
                // fallback prefix:name 형태도 제거
                for (String alias : command.getAliases()) {
                    knownCommands.remove(alias);
                }
            }
        } catch (ReflectiveOperationException exception) {
            // 제거 실패는 치명적이지 않으므로 FINE 로 처리
            command.getPlugin().getLogger().log(Level.FINE,
                    "[JobCore] 커맨드 unregister 실패: " + command.getName(), exception);
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

    @SuppressWarnings("unchecked")
    private static Map<String, org.bukkit.command.Command> resolveKnownCommands(CommandMap commandMap)
            throws ReflectiveOperationException {
        try {
            Method knownCommandsMethod = commandMap.getClass().getDeclaredMethod("getKnownCommands");
            knownCommandsMethod.setAccessible(true);
            return (Map<String, org.bukkit.command.Command>) knownCommandsMethod.invoke(commandMap);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}

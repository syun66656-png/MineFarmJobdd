package kr.minefarm.job.jobcore.api;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 직업 모듈이 JobCore 서비스에 접근할 때 사용하는 컨텍스트.
 * 모듈은 JobCore 내부 구현(database.repository 등)에 직접 의존하지 않는다.
 */
public interface JobContext {

    JavaPlugin getPlugin();

    JobCoreAPI getCore();

    void registerListener(Listener listener);

    void registerCommands();
}

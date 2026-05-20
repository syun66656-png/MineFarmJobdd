package kr.minefarm.job.jobcore.api;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * JobCore 플러그인이 직업 확장 모듈(JobMiner 등)에 노출하는 진입 API.
 * 구현·DB·GUI 등은 {@link JobCoreAPI} 외 패키지에 직접 의존하지 않는다.
 */
public interface JobCoreProvider {

    JobCoreAPI getApi();

    /**
     * {@link JobCoreBootstrap#loadJobModules()} 호출 전에만 등록 가능.
     *
     * @return 등록 성공 여부
     */
    boolean registerJobModule(JobModule module, JavaPlugin hostPlugin);

    /**
     * 호스트 플러그인 ClassLoader 에 있는 모듈 클래스를 이름으로 등록한다 (별도 JAR / 클래스로더 분리용).
     */
    boolean registerHostJobModule(JavaPlugin hostPlugin, String moduleClassName);

    boolean isModulesLoaded();
}

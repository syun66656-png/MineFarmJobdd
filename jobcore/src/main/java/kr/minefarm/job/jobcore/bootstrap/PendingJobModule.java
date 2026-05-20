package kr.minefarm.job.jobcore.bootstrap;

import kr.minefarm.job.jobcore.api.JobModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 등록 대기 중인 직업 모듈 + 호스트 플러그인(리스너·명령·데이터 폴더).
 */
public record PendingJobModule(JobModule module, JavaPlugin hostPlugin) {
}

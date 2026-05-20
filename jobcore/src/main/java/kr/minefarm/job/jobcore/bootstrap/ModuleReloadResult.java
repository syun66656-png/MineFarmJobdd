package kr.minefarm.job.jobcore.bootstrap;

/**
 * 모듈·코어 리로드 결과.
 */
public sealed interface ModuleReloadResult permits ModuleReloadResult.Success, ModuleReloadResult.Failure {

    String targetId();

    record Success(String targetId) implements ModuleReloadResult {
    }

    record Failure(String targetId, String message, Throwable cause) implements ModuleReloadResult {
    }

    static Success ok(String targetId) {
        return new Success(targetId);
    }

    static Failure fail(String targetId, String message, Throwable cause) {
        return new Failure(targetId, message, cause);
    }
}

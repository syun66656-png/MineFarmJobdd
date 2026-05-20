package kr.minefarm.job.jobcore.registry;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.domain.JobId;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 활성화된 {@link Job} 구현체 등록소.
 * 직업 모듈이 enable 시 자신의 Job을 등록한다.
 */
public final class JobRegistry {

    private final Map<JobId, Job> jobs = new EnumMap<>(JobId.class);

    public void register(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("job must not be null");
        }
        if (job.getId() == JobId.NONE) {
            throw new IllegalArgumentException("Cannot register NONE job");
        }
        if (jobs.containsKey(job.getId())) {
            throw new IllegalStateException("Job already registered: " + job.getId());
        }
        jobs.put(job.getId(), job);
    }

    public void unregister(JobId jobId) {
        jobs.remove(jobId);
    }

    public Optional<Job> find(JobId jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Collection<Job> getAll() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public boolean isRegistered(JobId jobId) {
        return jobs.containsKey(jobId);
    }
}

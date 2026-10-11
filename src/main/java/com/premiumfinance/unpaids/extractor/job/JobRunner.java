package com.premiumfinance.unpaids.extractor.job;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the job when the container starts and turns the result into the process exit code: 0 when
 * everything picked up was published, otherwise 1, so a failed execution shows in ACA and can be
 * alerted on. Tests switch this off with {@code extractor.run-on-startup=false}.
 */
@Component
@ConditionalOnProperty(prefix = "extractor", name = "run-on-startup", havingValue = "true", matchIfMissing = true)
public class JobRunner implements ApplicationRunner, ExitCodeGenerator {

    private final ExtractionJob job;
    private int exitCode;

    public JobRunner(ExtractionJob job) {
        this.job = job;
    }

    @Override
    public void run(ApplicationArguments args) {
        exitCode = job.run().successful() ? 0 : 1;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

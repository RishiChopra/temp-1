package com.premiumfinance.unpaids.extractor.dailyfeed;

import java.util.UUID;

/** Identifies one execution of the job; it is what a claimed daily feed is claimed by. */
public record ExecutionId(String value) {

    private static final int MAX_LENGTH = 100;

    /**
     * ACA sets CONTAINER_APP_JOB_EXECUTION_NAME (HOSTNAME elsewhere); the random suffix tells apart
     * retries of the same execution.
     */
    public static ExecutionId next() {
        String base = System.getenv("CONTAINER_APP_JOB_EXECUTION_NAME");
        if (base == null || base.isBlank()) {
            base = System.getenv().getOrDefault("HOSTNAME", "local");
        }
        String id = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new ExecutionId(id.length() <= MAX_LENGTH ? id : id.substring(id.length() - MAX_LENGTH));
    }

    @Override
    public String toString() {
        return value;
    }
}

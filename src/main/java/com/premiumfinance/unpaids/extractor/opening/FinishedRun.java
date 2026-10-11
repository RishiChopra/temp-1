package com.premiumfinance.unpaids.extractor.opening;

import java.time.LocalDateTime;

/**
 * An unpaids run that finished successfully.
 *
 * @param ref     identifies the run (job id and end time), so a repeated run can be told apart
 * @param endedAt when it finished, for the log
 */
public record FinishedRun(String ref, LocalDateTime endedAt) {
}

package com.premiumfinance.unpaids.extractor.opening;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The monolith's record of unpaids runs (I_SCHEDULED_JOBS in the main database today). Only runs that
 * finished successfully count: a failed run never writes an end time.
 */
public interface FinishedUnpaidsRuns {

    /** The most recent successful run of the job that started on or after {@code notBefore}. */
    Optional<FinishedRun> latest(int jobId, LocalDate notBefore);
}

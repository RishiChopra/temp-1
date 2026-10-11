package com.premiumfinance.unpaids.extractor.dailyfeed;

import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Where daily feeds are kept (UNPAID_DAILY_FEED in the main database).
 *
 * <p>Claiming is exclusive: two executions can never work on the same daily feed at the same time.
 */
public interface DailyFeedStore {

    /**
     * Opens the feed for a business date whose unpaids run has finished, or re-opens a finished day when
     * the run was repeated (a new run reference).
     *
     * @return true if a daily feed was opened or re-opened
     */
    boolean open(UnpaidFeed feed, LocalDate businessDate, String unpaidsRunRef);

    /** Opens the feed for a business date that does not depend on an unpaids run (manual feeds). */
    boolean open(UnpaidFeed feed, LocalDate businessDate);

    /** The business date an unpaids run was already used for, if any. */
    Optional<LocalDate> businessDateOfRun(UnpaidFeed feed, String unpaidsRunRef);

    /** Closes the feed's daily feeds for business dates before {@code businessDate}. @return how many were closed */
    int closeDaysBefore(UnpaidFeed feed, LocalDate businessDate);

    /** Daily feeds that may have work to do; {@link #claim} makes the final decision. */
    List<DailyFeed> findWorkable();

    /** @return true if this execution now owns the daily feed */
    boolean claim(DailyFeed dailyFeed, ExecutionId executionId);

    /** Records the outcome of this execution's attempt and adds its counts. */
    void finish(DailyFeed dailyFeed, ExecutionId executionId, FeedOutcome outcome);
}

package com.premiumfinance.unpaids.extractor.dailyfeed;

/**
 * How an attempt at a daily feed ended. Each outcome knows the status it leaves the daily feed in.
 */
public sealed interface FeedOutcome {

    FeedCounts counts();

    DailyFeedStatus status();

    /** Why the daily feed is not done; null when it is. */
    String reason();

    /** Everything staged was published, nothing is left in the ledger. */
    record Done(FeedCounts counts) implements FeedOutcome {

        @Override
        public DailyFeedStatus status() {
            return DailyFeedStatus.DONE;
        }

        @Override
        public String reason() {
            return null;
        }
    }

    /** Something is left to do; the next execution picks the daily feed up again. */
    record RetryLater(FeedCounts counts, String reason) implements FeedOutcome {

        @Override
        public DailyFeedStatus status() {
            return DailyFeedStatus.FAILED;
        }
    }

    /** Retrying will not help; an operator has to look (db/runbook.sql). */
    record NeedsAttention(FeedCounts counts, String reason) implements FeedOutcome {

        @Override
        public DailyFeedStatus status() {
            return DailyFeedStatus.ATTENTION;
        }
    }
}

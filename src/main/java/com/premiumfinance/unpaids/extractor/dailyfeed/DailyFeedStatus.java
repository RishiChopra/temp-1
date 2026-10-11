package com.premiumfinance.unpaids.extractor.dailyfeed;

/** Lifecycle of one feed for one business date (a row in UNPAID_DAILY_FEED). */
public enum DailyFeedStatus {

    /** Opened, nothing done yet. */
    NEW,

    /** Claimed by an execution that is working on it. */
    IN_PROGRESS,

    /** Everything published. Manual feeds stay claimable for the rest of the day. */
    DONE,

    /** Last attempt did not finish; the next execution retries. */
    FAILED,

    /** Retries used up, or events could not be sent; needs an operator (db/runbook.sql). */
    ATTENTION,

    /** A manual feed for a previous day; no more work. */
    CLOSED
}

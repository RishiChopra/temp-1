package com.premiumfinance.unpaids.extractor.dailyfeed;

import java.time.LocalDate;

/**
 * One feed for one business date, e.g. "pl-batch on 2026-10-12". This is the unit of work: one execution
 * claims it, stages and publishes its unpaids, and records a {@link FeedOutcome}.
 */
public record DailyFeed(LocalDate businessDate, String feedId, DailyFeedStatus status, int attempts) {

    /** Attempts including the one in progress (the claim has already counted it in the table). */
    public int attemptsIncludingThisOne() {
        return attempts + 1;
    }

    @Override
    public String toString() {
        return feedId + " on " + businessDate;
    }
}

package com.premiumfinance.unpaids.extractor.processing;

import com.premiumfinance.unpaids.extractor.config.ExtractorProperties;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedCounts;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.NeedsAttention;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;
import com.premiumfinance.unpaids.extractor.ledger.Backlog;
import org.springframework.stereotype.Component;

/**
 * Decides how an attempt at a daily feed ended. Pure logic, no I/O:
 * <ul>
 *   <li>done only when nothing is left in the ledger for the feed;</li>
 *   <li>entries that used up their send attempts need an operator;</li>
 *   <li>anything else is retried, until the daily feed has used up its own attempts.</li>
 * </ul>
 */
@Component
public class FeedOutcomePolicy {

    private final int maxAttempts;

    public FeedOutcomePolicy(ExtractorProperties properties) {
        this.maxAttempts = properties.maxAttempts();
    }

    public FeedOutcome afterPublishing(DailyFeed dailyFeed, FeedCounts counts, Backlog backlog) {
        if (backlog.failed() > 0) {
            return new NeedsAttention(counts, backlog.failed() + " unpaid(s) could not be sent after every retry");
        }
        if (backlog.pending() > 0) {
            return retryOrGiveUp(dailyFeed, counts, backlog.pending() + " unpaid(s) waiting to retry");
        }
        return new Done(counts);
    }

    public FeedOutcome afterError(DailyFeed dailyFeed, FeedCounts counts, Exception error) {
        return retryOrGiveUp(dailyFeed, counts, error.toString());
    }

    private FeedOutcome retryOrGiveUp(DailyFeed dailyFeed, FeedCounts counts, String reason) {
        if (dailyFeed.attemptsIncludingThisOne() >= maxAttempts) {
            return new NeedsAttention(counts, "gave up after " + maxAttempts + " attempts: " + reason);
        }
        return new RetryLater(counts, reason);
    }
}

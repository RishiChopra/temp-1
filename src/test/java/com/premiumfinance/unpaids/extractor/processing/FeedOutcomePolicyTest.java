package com.premiumfinance.unpaids.extractor.processing;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedCounts;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.NeedsAttention;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;
import com.premiumfinance.unpaids.extractor.ledger.Backlog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedOutcomePolicyTest {

    private final FeedOutcomePolicy policy = new FeedOutcomePolicy(TestFixtures.extractorProperties());
    private final FeedCounts counts = new FeedCounts(3, 0, 3, 0);

    @Test
    void doneWhenNothingIsLeftInTheLedger() {
        var outcome = policy.afterPublishing(firstAttempt(), counts, new Backlog(0, 0));

        assertThat(outcome).isEqualTo(new Done(counts));
    }

    @Test
    void retriedWhileEntriesAreWaitingToResend() {
        var outcome = policy.afterPublishing(firstAttempt(), counts, new Backlog(2, 0));

        assertThat(outcome).isInstanceOf(RetryLater.class);
        assertThat(outcome.reason()).contains("2 unpaid(s) waiting to retry");
    }

    @Test
    void needsAttentionAsSoonAsAnEntryHasUsedUpItsSendAttempts() {
        var outcome = policy.afterPublishing(firstAttempt(), counts, new Backlog(5, 1));

        assertThat(outcome).isInstanceOf(NeedsAttention.class);
    }

    @Test
    void givesUpOnTheLastAttempt() {
        var lastAttempt = TestFixtures.dailyFeed(TestFixtures.plBatchFeed(), TestFixtures.MAX_ATTEMPTS - 1);

        var outcome = policy.afterError(lastAttempt, FeedCounts.NONE, new IllegalStateException("db down"));

        assertThat(outcome).isInstanceOf(NeedsAttention.class);
        assertThat(outcome.reason()).startsWith("gave up after 10 attempts").contains("db down");
    }

    @Test
    void anErrorBeforeTheLastAttemptIsRetried() {
        var outcome = policy.afterError(firstAttempt(), FeedCounts.NONE, new IllegalStateException("db down"));

        assertThat(outcome).isInstanceOf(RetryLater.class);
    }

    private static DailyFeed firstAttempt() {
        return TestFixtures.dailyFeed(TestFixtures.plBatchFeed(), 0);
    }
}

package com.premiumfinance.unpaids.extractor.processing;

import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedCounts;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.NeedsAttention;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.ledger.Staged;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedger;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedgers;
import com.premiumfinance.unpaids.extractor.metrics.ExtractorMetrics;
import com.premiumfinance.unpaids.extractor.publish.PublishResult;
import com.premiumfinance.unpaids.extractor.publish.UnpaidEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Works through one daily feed: claim it, stage its new unpaids, publish them, decide the outcome and
 * record it.
 *
 * <p>Every step can safely be repeated, so whatever goes wrong the next execution carries on from what is
 * still pending in the ledger.
 */
@Service
public class DailyFeedProcessor {

    private static final Logger log = LoggerFactory.getLogger(DailyFeedProcessor.class);

    private final DailyFeedStore dailyFeeds;
    private final UnpaidLedgers ledgers;
    private final UnpaidEventPublisher publisher;
    private final FeedOutcomePolicy policy;
    private final ExtractorMetrics metrics;

    public DailyFeedProcessor(DailyFeedStore dailyFeeds, UnpaidLedgers ledgers, UnpaidEventPublisher publisher,
                              FeedOutcomePolicy policy, ExtractorMetrics metrics) {
        this.dailyFeeds = dailyFeeds;
        this.ledgers = ledgers;
        this.publisher = publisher;
        this.policy = policy;
        this.metrics = metrics;
    }

    /** @return the outcome, or empty when another execution has the daily feed or it has nothing to do */
    public Optional<FeedOutcome> process(DailyFeed dailyFeed, UnpaidFeed feed, ExecutionId executionId) {
        if (!dailyFeeds.claim(dailyFeed, executionId)) {
            return Optional.empty();
        }
        try (var _ = MDC.putCloseable("feed", feed.id());
             var _ = MDC.putCloseable("businessDate", dailyFeed.businessDate().toString())) {

            FeedOutcome outcome = metrics.timeDailyFeed(feed, () -> attempt(dailyFeed, feed));
            record(dailyFeed, executionId, outcome);
            return Optional.of(outcome);
        }
    }

    private FeedOutcome attempt(DailyFeed dailyFeed, UnpaidFeed feed) {
        UnpaidLedger ledger = ledgers.of(feed.loanBook());
        FeedCounts counts = FeedCounts.NONE;
        try {
            Staged staged = ledger.stage(feed, dailyFeed.businessDate());
            counts = FeedCounts.staged(staged.staged(), staged.collisions());
            reportCollisions(feed, staged);

            PublishResult published = publisher.publishPending(feed);
            counts = counts.withPublished(published.sent(), published.invalid());

            return policy.afterPublishing(dailyFeed, counts, ledger.backlog(feed));
        } catch (RuntimeException e) {
            log.error("{} failed", dailyFeed, e);
            return policy.afterError(dailyFeed, counts, e);
        }
    }

    private void record(DailyFeed dailyFeed, ExecutionId executionId, FeedOutcome outcome) {
        switch (outcome) {
            case Done(var counts) -> log.info("{} done: {}", dailyFeed, counts);
            case RetryLater(var _, var reason) -> log.warn("{} will be retried: {}", dailyFeed, reason);
            case NeedsAttention(var _, var reason) ->
                    log.error("alert=DAILY_FEED_NEEDS_ATTENTION dailyFeed=\"{}\" reason=\"{}\"", dailyFeed, reason);
        }
        try {
            dailyFeeds.finish(dailyFeed, executionId, outcome);
        } catch (RuntimeException e) {
            // Main database unavailable: the claim expires and a later execution picks the daily feed up again.
            log.error("Could not record {} as {}; it is retried once the claim expires", dailyFeed, outcome.status(), e);
        }
    }

    private void reportCollisions(UnpaidFeed feed, Staged staged) {
        if (staged.collisions() > 0) {
            metrics.collisions(feed, staged.collisions());
            log.warn("alert=UNPAID_COLLISION feed={} count={} (ledger status COLLISION)", feed.id(), staged.collisions());
        }
    }
}

package com.premiumfinance.unpaids.extractor.processing;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedCounts;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.ledger.Backlog;
import com.premiumfinance.unpaids.extractor.ledger.Staged;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedger;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedgers;
import com.premiumfinance.unpaids.extractor.metrics.ExtractorMetrics;
import com.premiumfinance.unpaids.extractor.publish.PublishResult;
import com.premiumfinance.unpaids.extractor.publish.UnpaidEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyFeedProcessorTest {

    private final ExecutionId executionId = new ExecutionId("exec-1");
    private final UnpaidFeed batchFeed = TestFixtures.plBatchFeed();
    private final DailyFeed dailyFeed = TestFixtures.dailyFeed(batchFeed, 0);

    private DailyFeedStore dailyFeeds;
    private UnpaidLedger ledger;
    private UnpaidEventPublisher publisher;
    private DailyFeedProcessor processor;

    @BeforeEach
    void setUp() {
        dailyFeeds = mock(DailyFeedStore.class);
        ledger = mock(UnpaidLedger.class);
        when(ledger.loanBook()).thenReturn(LoanBook.PL);
        publisher = mock(UnpaidEventPublisher.class);
        processor = new DailyFeedProcessor(dailyFeeds, new UnpaidLedgers(List.of(ledger)), publisher,
                new FeedOutcomePolicy(TestFixtures.extractorProperties()), new ExtractorMetrics(new SimpleMeterRegistry()));

        when(dailyFeeds.claim(dailyFeed, executionId)).thenReturn(true);
        when(ledger.stage(batchFeed, dailyFeed.businessDate())).thenReturn(new Staged(3, 1));
        when(publisher.publishPending(batchFeed)).thenReturn(new PublishResult(2, 1, false));
    }

    @Test
    void aDailyFeedClaimedByAnotherExecutionIsLeftAlone() {
        when(dailyFeeds.claim(dailyFeed, executionId)).thenReturn(false);

        assertThat(processor.process(dailyFeed, batchFeed, executionId)).isEmpty();
        verify(ledger, never()).stage(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void publishesAndRecordsDoneWhenNothingIsLeft() {
        when(ledger.backlog(batchFeed)).thenReturn(new Backlog(0, 0));

        FeedOutcome outcome = processor.process(dailyFeed, batchFeed, executionId).orElseThrow();

        var counts = new FeedCounts(3, 1, 2, 1);
        assertThat(outcome).isEqualTo(new Done(counts));
        verify(dailyFeeds).finish(dailyFeed, executionId, outcome);
    }

    @Test
    void aDayWithWorkLeftIsRetried() {
        when(ledger.backlog(batchFeed)).thenReturn(new Backlog(2, 0));

        FeedOutcome outcome = processor.process(dailyFeed, batchFeed, executionId).orElseThrow();

        assertThat(outcome).isInstanceOf(RetryLater.class);
        verify(dailyFeeds).finish(dailyFeed, executionId, outcome);
    }

    @Test
    void anErrorKeepsTheCountsSoFarAndIsRecordedForRetry() {
        doThrow(new IllegalStateException("Service Bus down")).when(publisher).publishPending(batchFeed);

        FeedOutcome outcome = processor.process(dailyFeed, batchFeed, executionId).orElseThrow();

        assertThat(outcome).isInstanceOf(RetryLater.class);
        assertThat(outcome.counts()).isEqualTo(FeedCounts.staged(3, 1));
        assertThat(outcome.reason()).contains("Service Bus down");
    }

    @Test
    void failingToRecordTheOutcomeDoesNotFailTheExecution() {
        when(ledger.backlog(batchFeed)).thenReturn(new Backlog(0, 0));
        doThrow(new IllegalStateException("main db down")).when(dailyFeeds).finish(any(), any(), any());

        assertThat(processor.process(dailyFeed, batchFeed, executionId)).containsInstanceOf(Done.class);
    }
}

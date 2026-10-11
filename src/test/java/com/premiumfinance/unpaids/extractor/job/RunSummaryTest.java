package com.premiumfinance.unpaids.extractor.job;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessDates;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedCounts;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.NeedsAttention;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunSummaryTest {

    private static final BusinessDates DATES =
            new BusinessDates(Map.of(LoanBook.PL, LocalDate.of(2026, 12, 15), LoanBook.CL, LocalDate.of(2026, 12, 15)));

    private final ExecutionId id = new ExecutionId("exec-1");

    @Test
    void countsEachKindOfOutcome() {
        var summary = RunSummary.of(id, DATES, true, List.of(
                new Done(FeedCounts.NONE), new Done(FeedCounts.NONE),
                new RetryLater(FeedCounts.NONE, "x"), new NeedsAttention(FeedCounts.NONE, "y")), 1, Duration.ZERO);

        assertThat(summary.done()).isEqualTo(2);
        assertThat(summary.retrying()).isEqualTo(1);
        assertThat(summary.needingAttention()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.successful()).isFalse();
    }

    @Test
    void successfulWhenEverythingPickedUpIsDone() {
        var summary = RunSummary.of(id, DATES, true, List.of(new Done(FeedCounts.NONE)), 0, Duration.ZERO);

        assertThat(summary.successful()).isTrue();
    }

    @Test
    void notSuccessfulWhenAFeedCouldNotBeOpened() {
        assertThat(RunSummary.of(id, DATES, false, List.of(), 0, Duration.ZERO).successful()).isFalse();
    }
}

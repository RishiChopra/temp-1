package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A run belongs to the business date that is current when it is first seen, whatever the wall clock
 * says. This is what lets testers move eodc_date on, several business dates in one real day.
 */
class BatchFeedOpenerTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 12, 15);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 12, 16);

    private final UnpaidFeed feed = TestFixtures.plBatchFeed();
    private final FinishedRun morningRun = new FinishedRun("133@2026-10-11T10:05:00", LocalDateTime.of(2026, 10, 11, 10, 5));
    private final FinishedRun afternoonRun = new FinishedRun("133@2026-10-11T14:40:00", LocalDateTime.of(2026, 10, 11, 14, 40));

    private FinishedUnpaidsRuns finishedRuns;
    private DailyFeedStore dailyFeeds;
    private BatchFeedOpener opener;

    @BeforeEach
    void setUp() {
        finishedRuns = mock(FinishedUnpaidsRuns.class);
        dailyFeeds = mock(DailyFeedStore.class);
        opener = new BatchFeedOpener(finishedRuns, dailyFeeds, TestFixtures.extractorProperties());
        when(dailyFeeds.businessDateOfRun(any(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void aNewFinishedRunOpensTheCurrentBusinessDate() {
        when(finishedRuns.latest(133, TestFixtures.GO_LIVE)).thenReturn(Optional.of(morningRun));

        opener.openDueDays(feed, DAY_1);

        verify(dailyFeeds).open(feed, DAY_1, morningRun.ref());
    }

    @Test
    void aRunAlreadyUsedForAnEarlierBusinessDateDoesNotOpenTheNextOne() {
        // eodc_date moved on to day 2, but day 2's unpaids run has not finished yet
        when(finishedRuns.latest(133, TestFixtures.GO_LIVE)).thenReturn(Optional.of(morningRun));
        when(dailyFeeds.businessDateOfRun(feed, morningRun.ref())).thenReturn(Optional.of(DAY_1));

        opener.openDueDays(feed, DAY_2);

        verify(dailyFeeds, never()).open(any(), any(), anyString());
    }

    @Test
    void secondBusinessDateOnTheSameRealDayIsOpenedByItsOwnRun() {
        when(finishedRuns.latest(133, TestFixtures.GO_LIVE)).thenReturn(Optional.of(afternoonRun));
        when(dailyFeeds.businessDateOfRun(feed, morningRun.ref())).thenReturn(Optional.of(DAY_1));

        opener.openDueDays(feed, DAY_2);

        verify(dailyFeeds).open(feed, DAY_2, afternoonRun.ref());
    }

    @Test
    void noFinishedRunOpensNothing() {
        when(finishedRuns.latest(133, TestFixtures.GO_LIVE)).thenReturn(Optional.empty());

        opener.openDueDays(feed, DAY_1);

        verify(dailyFeeds, never()).open(any(), any(), anyString());
    }
}

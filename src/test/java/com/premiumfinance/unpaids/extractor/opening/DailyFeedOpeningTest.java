package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.businessdate.BusinessDates;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.FeedCatalog;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyFeedOpeningTest {

    private static final LocalDate PL_DATE = LocalDate.of(2026, 12, 15);
    private static final BusinessDates BUSINESS_DATES =
            new BusinessDates(Map.of(LoanBook.PL, PL_DATE, LoanBook.CL, LocalDate.of(2026, 12, 14)));

    private final FeedCatalog feeds = new FeedCatalog(TestFixtures.extractorProperties(), new DefaultResourceLoader());
    private final FeedOpener batchOpener = opener(UnpaidOrigin.BATCH);
    private final FeedOpener manualOpener = opener(UnpaidOrigin.MANUAL);

    @Test
    void eachFeedIsOpenedByTheOpenerForItsOriginOnItsLoanBooksBusinessDate() {
        var opening = new DailyFeedOpening(feeds, List.of(batchOpener, manualOpener));

        assertThat(opening.openDueFeeds(BUSINESS_DATES)).isTrue();
        verify(batchOpener).openDueDays(TestFixtures.plBatchFeed(), PL_DATE);
        verify(manualOpener).openDueDays(TestFixtures.plManualFeed(), PL_DATE);
    }

    @Test
    void oneFeedFailingDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("main db down")).when(batchOpener).openDueDays(TestFixtures.plBatchFeed(), PL_DATE);
        var opening = new DailyFeedOpening(feeds, List.of(batchOpener, manualOpener));

        assertThat(opening.openDueFeeds(BUSINESS_DATES)).isFalse();
        verify(manualOpener).openDueDays(TestFixtures.plManualFeed(), PL_DATE);
    }

    @Test
    void everyOriginNeedsAnOpener() {
        assertThatThrownBy(() -> new DailyFeedOpening(feeds, List.of(batchOpener)))
                .hasMessageContaining("No FeedOpener for MANUAL");
    }

    private static FeedOpener opener(UnpaidOrigin origin) {
        var opener = mock(FeedOpener.class);
        when(opener.origin()).thenReturn(origin);
        return opener;
    }
}

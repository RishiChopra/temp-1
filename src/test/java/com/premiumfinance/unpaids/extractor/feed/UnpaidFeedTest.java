package com.premiumfinance.unpaids.extractor.feed;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnpaidFeedTest {

    @Test
    void lookbackWindowNeverStartsBeforeGoLive() {
        var feed = TestFixtures.plBatchFeed(); // 7 days lookback

        assertThat(feed.windowStart(LocalDate.of(2026, 10, 8), TestFixtures.GO_LIVE)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(feed.windowStart(LocalDate.of(2026, 1, 3), TestFixtures.GO_LIVE)).isEqualTo(TestFixtures.GO_LIVE);
    }

    @Test
    void aFeedThatFollowsTheUnpaidsRunNeedsItsJobId() {
        assertThatThrownBy(() -> new UnpaidFeed("pl-batch", true, LoanBook.PL, UnpaidOrigin.BATCH, "UNPAID_DETAILS",
                "classpath:sql/feeds/pl/unpaid-details.sql", 7, null, Map.of()))
                .hasMessageContaining("unpaids-job-id");
    }

    @Test
    void onlyBatchUnpaidsFollowTheUnpaidsRun() {
        assertThat(UnpaidOrigin.BATCH.followsUnpaidsRun()).isTrue();
        assertThat(UnpaidOrigin.MANUAL.followsUnpaidsRun()).isFalse();
    }
}

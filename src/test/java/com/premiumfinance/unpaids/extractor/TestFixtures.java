package com.premiumfinance.unpaids.extractor;

import com.premiumfinance.unpaids.extractor.config.ExtractorProperties;
import com.premiumfinance.unpaids.extractor.config.HousekeepingProperties;
import com.premiumfinance.unpaids.extractor.config.PublishProperties;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStatus;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Hand-built settings and data for unit tests (no Spring context). */
public final class TestFixtures {

    public static final LocalDate GO_LIVE = LocalDate.of(2026, 1, 1);
    public static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 7);
    public static final int MAX_ATTEMPTS = 10;

    private TestFixtures() {
    }

    public static ExtractorProperties extractorProperties() {
        return new ExtractorProperties(GO_LIVE, ZoneId.of("Europe/London"),
                Duration.ofMinutes(35), MAX_ATTEMPTS, List.of(plBatchFeed(), plManualFeed()));
    }

    public static PublishProperties publishProperties() {
        return publishProperties(3);
    }

    public static PublishProperties publishProperties(int chunkSize) {
        return new PublishProperties(chunkSize, 3, Duration.ofMinutes(1), Duration.ofMinutes(30),
                "UnpaidRecorded", "1.0", "urn:test:unpaids", "^[A-Z0-9-]{1,30}$");
    }

    public static HousekeepingProperties housekeepingProperties() {
        return new HousekeepingProperties(true, 400, 1000);
    }

    public static UnpaidFeed plBatchFeed() {
        return new UnpaidFeed("pl-batch", true, LoanBook.PL, UnpaidOrigin.BATCH, "UNPAID_DETAILS",
                "classpath:sql/feeds/pl/unpaid-details.sql", 7, 133, Map.of());
    }

    public static UnpaidFeed plManualFeed() {
        return new UnpaidFeed("pl-manual", true, LoanBook.PL, UnpaidOrigin.MANUAL, "MANUAL_TRANSACTIONS",
                "classpath:sql/feeds/pl/manual-unpaids.sql", 30, null,
                Map.of("manualTxnTypes", List.of("MUNP", "MUNP2"),
                        "approvedStatus", List.of("APPROVED"),
                        "businessDate", List.of("must-not-override")));
    }

    public static DailyFeed dailyFeed(UnpaidFeed feed, int attempts) {
        return new DailyFeed(BUSINESS_DATE, feed.id(), DailyFeedStatus.IN_PROGRESS, attempts);
    }

    public static LedgerEntry ledgerEntry(long ledgerId, String loanNumber) {
        return new LedgerEntry(ledgerId, "00000000-0000-0000-0000-00000000000" + (ledgerId % 10),
                LoanBook.PL, UnpaidOrigin.BATCH, "UNPAID_DETAILS", String.valueOf(ledgerId), loanNumber,
                BUSINESS_DATE, "UnpaidRecorded", BUSINESS_DATE,
                OffsetDateTime.of(2026, 10, 8, 1, 30, 0, 0, ZoneOffset.UTC));
    }
}

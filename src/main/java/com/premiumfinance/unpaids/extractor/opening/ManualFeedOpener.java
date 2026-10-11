package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Manual unpaids are approved all day: their feed is open for the loan book's business date, and earlier
 * business dates are closed.
 */
@Component
public class ManualFeedOpener implements FeedOpener {

    private static final Logger log = LoggerFactory.getLogger(ManualFeedOpener.class);

    private final DailyFeedStore dailyFeeds;

    public ManualFeedOpener(DailyFeedStore dailyFeeds) {
        this.dailyFeeds = dailyFeeds;
    }

    @Override
    public UnpaidOrigin origin() {
        return UnpaidOrigin.MANUAL;
    }

    @Override
    public void openDueDays(UnpaidFeed feed, LocalDate businessDate) {
        if (dailyFeeds.open(feed, businessDate)) {
            log.info("Opened {} for business date {}", feed.id(), businessDate);
        }
        int closed = dailyFeeds.closeDaysBefore(feed, businessDate);
        if (closed > 0) {
            log.info("Closed {} earlier day(s) of {}", closed, feed.id());
        }
    }
}

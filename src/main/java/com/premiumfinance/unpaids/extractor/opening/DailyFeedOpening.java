package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessDates;
import com.premiumfinance.unpaids.extractor.feed.FeedCatalog;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Opens the daily feeds that have work to do, using the {@link FeedOpener} for each feed's origin.
 * A problem with one feed is logged and does not stop the others.
 */
@Component
public class DailyFeedOpening {

    private static final Logger log = LoggerFactory.getLogger(DailyFeedOpening.class);

    private final FeedCatalog feeds;
    private final Map<UnpaidOrigin, FeedOpener> openers = new EnumMap<>(UnpaidOrigin.class);

    public DailyFeedOpening(FeedCatalog feeds, List<FeedOpener> openers) {
        this.feeds = feeds;
        for (FeedOpener opener : openers) {
            if (this.openers.putIfAbsent(opener.origin(), opener) != null) {
                throw new IllegalStateException("More than one FeedOpener for " + opener.origin());
            }
        }
        for (UnpaidOrigin origin : UnpaidOrigin.values()) {
            if (!this.openers.containsKey(origin)) {
                throw new IllegalStateException("No FeedOpener for " + origin);
            }
        }
    }

    /** @return true if every feed was handled without error */
    public boolean openDueFeeds(BusinessDates businessDates) {
        boolean allOpened = true;
        for (UnpaidFeed feed : feeds.enabled()) {
            try {
                openers.get(feed.origin()).openDueDays(feed, businessDates.of(feed.loanBook()));
            } catch (RuntimeException e) {
                allOpened = false;
                log.error("Could not open daily feeds for '{}'; other feeds carry on", feed.id(), e);
            }
        }
        return allOpened;
    }
}

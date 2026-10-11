package com.premiumfinance.unpaids.extractor.housekeeping;

import com.premiumfinance.unpaids.extractor.config.HousekeepingProperties;
import com.premiumfinance.unpaids.extractor.feed.FeedCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deletes published ledger entries and finished daily feeds once they are past retention: one bounded
 * delete per table per execution. Pending, failed and collision entries are never touched.
 *
 * <p>Retention must be longer than every feed's lookback, otherwise a deleted entry still inside the window
 * would be staged and published again. This is checked at startup.
 */
@Service
public class Housekeeping {

    private static final Logger log = LoggerFactory.getLogger(Housekeeping.class);
    static final int SAFETY_MARGIN_DAYS = 7;

    private final List<ExpiredDataPurger> purgers;
    private final HousekeepingProperties properties;

    public Housekeeping(List<ExpiredDataPurger> purgers, HousekeepingProperties properties, FeedCatalog feeds) {
        this.purgers = purgers;
        this.properties = properties;

        int longestLookback = feeds.longestLookbackDays();
        if (properties.retentionDays() <= longestLookback + SAFETY_MARGIN_DAYS) {
            throw new IllegalStateException("extractor.housekeeping.retention-days (%d) must be more than the longest lookback-days (%d) + %d"
                    .formatted(properties.retentionDays(), longestLookback, SAFETY_MARGIN_DAYS));
        }
    }

    public void run() {
        if (!properties.enabled()) {
            return;
        }
        purgers.forEach(this::purge);
    }

    private void purge(ExpiredDataPurger purger) {
        try {
            int deleted = purger.deleteExpired(properties.retentionDays(), properties.deleteBatchSize());
            if (deleted > 0) {
                log.info("Housekeeping deleted {} old row(s) from the {}", deleted, purger.describe());
            }
        } catch (RuntimeException e) {
            log.warn("Housekeeping failed for the {}; it tries again next execution", purger.describe(), e);
        }
    }
}

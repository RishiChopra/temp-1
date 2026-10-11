package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.config.ExtractorProperties;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Opens a batch feed for the loan book's business date once a new unpaids run has finished.
 *
 * <p>I_SCHEDULED_JOBS only records times, while the unpaids run processes the monolith's business date
 * (eodc_date). So a run is matched to a business date like this: the latest successful run that has not
 * yet been used for any day belongs to the current business date. This holds when test environments
 * move the business date on, including several business dates in one real day. A repeated run on the
 * same business date gives a new run reference, which re-opens that day.
 */
@Component
public class BatchFeedOpener implements FeedOpener {

    private static final Logger log = LoggerFactory.getLogger(BatchFeedOpener.class);

    private final FinishedUnpaidsRuns finishedRuns;
    private final DailyFeedStore dailyFeeds;
    private final LocalDate goLiveDate;

    public BatchFeedOpener(FinishedUnpaidsRuns finishedRuns, DailyFeedStore dailyFeeds, ExtractorProperties properties) {
        this.finishedRuns = finishedRuns;
        this.dailyFeeds = dailyFeeds;
        this.goLiveDate = properties.goLiveDate();
    }

    @Override
    public UnpaidOrigin origin() {
        return UnpaidOrigin.BATCH;
    }

    @Override
    public void openDueDays(UnpaidFeed feed, LocalDate businessDate) {
        Optional<FinishedRun> latest = finishedRuns.latest(feed.unpaidsJobId(), goLiveDate);
        if (latest.isEmpty()) {
            return;
        }
        FinishedRun run = latest.get();
        Optional<LocalDate> alreadyUsedFor = dailyFeeds.businessDateOfRun(feed, run.ref());
        if (alreadyUsedFor.isPresent()) {
            log.debug("{}: latest unpaids run {} was already used for {}", feed.id(), run.ref(), alreadyUsedFor.get());
            return;
        }
        if (dailyFeeds.open(feed, businessDate, run.ref())) {
            log.info("Unpaids run {} finished at {}: opened {} for business date {}",
                    run.ref(), run.endedAt(), feed.id(), businessDate);
        }
    }
}

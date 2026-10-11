package com.premiumfinance.unpaids.extractor.job;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessCalendar;
import com.premiumfinance.unpaids.extractor.businessdate.BusinessDates;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.feed.FeedCatalog;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.housekeeping.Housekeeping;
import com.premiumfinance.unpaids.extractor.opening.DailyFeedOpening;
import com.premiumfinance.unpaids.extractor.processing.DailyFeedProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One execution of the extractor:
 * <ol>
 *   <li>read each loan book's business date from the monolith;</li>
 *   <li>open the daily feeds that are due (finished unpaids runs, and the business date's manual feeds);</li>
 *   <li>process each workable daily feed in turn;</li>
 *   <li>tidy up old rows.</li>
 * </ol>
 */
@Service
public class ExtractionJob {

    private static final Logger log = LoggerFactory.getLogger(ExtractionJob.class);

    private final BusinessCalendar calendar;
    private final DailyFeedOpening opening;
    private final DailyFeedStore dailyFeeds;
    private final FeedCatalog feeds;
    private final DailyFeedProcessor processor;
    private final Housekeeping housekeeping;
    private final Clock clock;

    public ExtractionJob(BusinessCalendar calendar, DailyFeedOpening opening, DailyFeedStore dailyFeeds,
                         FeedCatalog feeds, DailyFeedProcessor processor, Housekeeping housekeeping, Clock clock) {
        this.calendar = calendar;
        this.opening = opening;
        this.dailyFeeds = dailyFeeds;
        this.feeds = feeds;
        this.processor = processor;
        this.housekeeping = housekeeping;
        this.clock = clock;
    }

    public RunSummary run() {
        ExecutionId executionId = ExecutionId.next();
        try (var _ = MDC.putCloseable("executionId", executionId.value())) {
            Instant started = clock.instant();
            BusinessDates businessDates = BusinessDates.readFrom(calendar);
            log.info("Extractor started, business dates {}", businessDates);

            boolean allFeedsOpened = opening.openDueFeeds(businessDates);

            var outcomes = new ArrayList<FeedOutcome>();
            int skipped = 0;
            for (DailyFeed dailyFeed : dailyFeeds.findWorkable()) {
                Optional<FeedOutcome> outcome = process(dailyFeed, executionId);
                if (outcome.isPresent()) {
                    outcomes.add(outcome.get());
                } else {
                    skipped++;
                }
            }

            housekeeping.run();

            var summary = RunSummary.of(executionId, businessDates, allFeedsOpened, List.copyOf(outcomes), skipped,
                    Duration.between(started, clock.instant()));
            log.info("Extractor finished: {}", summary);
            return summary;
        }
    }

    private Optional<FeedOutcome> process(DailyFeed dailyFeed, ExecutionId executionId) {
        Optional<UnpaidFeed> feed = feeds.find(dailyFeed.feedId());
        if (feed.isEmpty()) {
            log.warn("Skipping {}: its feed is not configured or is disabled", dailyFeed);
            return Optional.empty();
        }
        return processor.process(dailyFeed, feed.get(), executionId);
    }
}

package com.premiumfinance.unpaids.extractor.metrics;

import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** The extractor's metrics, all tagged by feed. Names are stable: dashboards and alerts use them. */
@Component
public class ExtractorMetrics {

    private final MeterRegistry registry;

    public ExtractorMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void sent(UnpaidFeed feed, int count) {
        registry.counter("unpaids.events.sent", "feed", feed.id()).increment(count);
    }

    public void invalid(UnpaidFeed feed) {
        registry.counter("unpaids.events.invalid", "feed", feed.id()).increment();
    }

    public void sendFailed(UnpaidFeed feed, int count) {
        registry.counter("unpaids.events.send_failures", "feed", feed.id()).increment(count);
    }

    public void collisions(UnpaidFeed feed, int count) {
        registry.counter("unpaids.events.collisions", "feed", feed.id()).increment(count);
    }

    /** Times one attempt at a daily feed, tagged with the status it ended in. */
    public FeedOutcome timeDailyFeed(UnpaidFeed feed, Supplier<FeedOutcome> attempt) {
        Timer.Sample sample = Timer.start(registry);
        String status = "ERROR";
        try {
            FeedOutcome outcome = attempt.get();
            status = outcome.status().name();
            return outcome;
        } finally {
            sample.stop(registry.timer("unpaids.daily_feed.duration", "feed", feed.id(), "outcome", status));
        }
    }
}

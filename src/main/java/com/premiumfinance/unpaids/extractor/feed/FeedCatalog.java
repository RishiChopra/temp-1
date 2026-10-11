package com.premiumfinance.unpaids.extractor.feed;

import com.premiumfinance.unpaids.extractor.config.ExtractorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The enabled feeds. The configuration is checked at startup, so a mistake fails the deployment rather
 * than an execution in the middle of the day.
 */
@Component
public class FeedCatalog {

    private static final Logger log = LoggerFactory.getLogger(FeedCatalog.class);

    private final Map<String, UnpaidFeed> enabledFeeds;

    public FeedCatalog(ExtractorProperties properties, ResourceLoader resources) {
        var ids = new HashSet<String>();
        var sourceTables = new HashSet<String>();
        var enabled = new LinkedHashMap<String, UnpaidFeed>();

        for (UnpaidFeed feed : properties.feeds()) {
            if (!ids.add(feed.id())) {
                throw invalid("feed id '%s' is used twice", feed.id());
            }
            if (!sourceTables.add(feed.loanBook() + "/" + feed.sourceTable())) {
                throw invalid("two %s feeds read source table %s", feed.loanBook(), feed.sourceTable());
            }
            if (!resources.getResource(feed.query()).exists()) {
                throw invalid("query %s of feed '%s' does not exist", feed.query(), feed.id());
            }
            if (feed.enabled()) {
                enabled.put(feed.id(), feed);
            } else {
                log.info("Feed '{}' is disabled", feed.id());
            }
        }
        this.enabledFeeds = Collections.unmodifiableMap(enabled); // keeps the configured order
        log.info("Enabled feeds: {}", enabled.keySet());
    }

    public List<UnpaidFeed> enabled() {
        return List.copyOf(enabledFeeds.values());
    }

    public Optional<UnpaidFeed> find(String id) {
        return Optional.ofNullable(enabledFeeds.get(id));
    }

    public int longestLookbackDays() {
        return enabledFeeds.values().stream().mapToInt(UnpaidFeed::lookbackDays).max().orElse(0);
    }

    private static IllegalStateException invalid(String message, Object... args) {
        return new IllegalStateException("Invalid extractor.feeds configuration: " + message.formatted(args));
    }
}

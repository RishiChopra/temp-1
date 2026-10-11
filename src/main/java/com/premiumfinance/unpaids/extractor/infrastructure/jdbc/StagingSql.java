package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the two staging statements for a feed: {@code ledger/stage.sql} and {@code ledger/collisions.sql},
 * each with the feed's own query in place of {@value #FEED_QUERY}, and their binds.
 */
final class StagingSql {

    static final String FEED_QUERY = "{{FEED_QUERY}}";

    private final SqlLoader sql;
    private final LocalDate goLiveDate;
    private final String eventType;

    StagingSql(SqlLoader sql, LocalDate goLiveDate, String eventType) {
        this.sql = sql;
        this.goLiveDate = goLiveDate;
        this.eventType = eventType;
    }

    String stage(UnpaidFeed feed) {
        return withFeedQuery(sql.named("ledger/stage"), sql.load(feed.query()));
    }

    String collisions(UnpaidFeed feed) {
        return withFeedQuery(sql.named("ledger/collisions"), sql.load(feed.query()));
    }

    /** The feed's own binds go in first, so the standard ones always win. */
    Map<String, Object> binds(UnpaidFeed feed, LocalDate businessDate) {
        var binds = new HashMap<String, Object>();
        feed.queryParams().forEach((name, values) -> binds.put(name, values.size() == 1 ? values.getFirst() : values));
        binds.put("loanBook", feed.loanBook().name());
        binds.put("origin", feed.origin().name());
        binds.put("sourceTable", feed.sourceTable());
        binds.put("eventType", eventType);
        binds.put("businessDate", businessDate);
        binds.put("windowStart", feed.windowStart(businessDate, goLiveDate));
        binds.put("goLiveDate", goLiveDate);
        return binds;
    }

    /** On its own lines, so a trailing "-- comment" in the feed query cannot swallow the closing bracket. */
    static String withFeedQuery(String template, String feedQuery) {
        if (!template.contains(FEED_QUERY)) {
            throw new IllegalStateException("SQL template is missing " + FEED_QUERY);
        }
        return template.replace(FEED_QUERY, "\n" + feedQuery + "\n");
    }
}

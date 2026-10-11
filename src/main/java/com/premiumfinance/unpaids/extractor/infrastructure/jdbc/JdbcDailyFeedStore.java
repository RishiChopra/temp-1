package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeed;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStatus;
import com.premiumfinance.unpaids.extractor.dailyfeed.DailyFeedStore;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.housekeeping.ExpiredDataPurger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * UNPAID_DAILY_FEED in the main database ({@code sql/daily-feed}). Every change of status is one guarded
 * UPDATE, so two executions can never work on the same daily feed at the same time.
 */
public class JdbcDailyFeedStore implements DailyFeedStore, ExpiredDataPurger {

    private static final Logger log = LoggerFactory.getLogger(JdbcDailyFeedStore.class);

    private final JdbcClient jdbc;
    private final SqlLoader sql;
    private final LocalDate goLiveDate;
    private final long claimExpirySeconds;
    private final int maxAttempts;

    public JdbcDailyFeedStore(DataSource mainDatabase, SqlLoader sql, LocalDate goLiveDate,
                              Duration claimExpiresAfter, int maxAttempts) {
        this.jdbc = JdbcClient.create(mainDatabase);
        this.sql = sql;
        this.goLiveDate = goLiveDate;
        this.claimExpirySeconds = claimExpiresAfter.toSeconds();
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean open(UnpaidFeed feed, LocalDate businessDate, String unpaidsRunRef) {
        try {
            return jdbc.sql(sql.named("daily-feed/open"))
                    .param("businessDate", businessDate)
                    .param("feedId", feed.id())
                    .param("loanBook", feed.loanBook().name())
                    .param("origin", feed.origin().name())
                    .param("unpaidsRunRef", unpaidsRunRef, Types.VARCHAR)
                    .update() > 0;
        } catch (DuplicateKeyException openedConcurrently) {
            return false;
        }
    }

    @Override
    public boolean open(UnpaidFeed feed, LocalDate businessDate) {
        return open(feed, businessDate, null);
    }

    @Override
    public Optional<LocalDate> businessDateOfRun(UnpaidFeed feed, String unpaidsRunRef) {
        return jdbc.sql(sql.named("daily-feed/business-date-of-run"))
                .param("feedId", feed.id())
                .param("unpaidsRunRef", unpaidsRunRef)
                .query((rs, _) -> rs.getDate("business_date").toLocalDate())
                .optional();
    }

    @Override
    public int closeDaysBefore(UnpaidFeed feed, LocalDate businessDate) {
        return jdbc.sql(sql.named("daily-feed/close-earlier-manual"))
                .param("feedId", feed.id())
                .param("businessDate", businessDate)
                .param("claimExpirySeconds", claimExpirySeconds)
                .update();
    }

    @Override
    public List<DailyFeed> findWorkable() {
        return jdbc.sql(sql.named("daily-feed/find-open"))
                .param("fromDate", goLiveDate)
                .param("maxAttempts", maxAttempts)
                .query((rs, _) -> new DailyFeed(
                        rs.getDate("business_date").toLocalDate(),
                        rs.getString("feed_id"),
                        DailyFeedStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempts")))
                .list();
    }

    @Override
    public boolean claim(DailyFeed dailyFeed, ExecutionId executionId) {
        return jdbc.sql(sql.named("daily-feed/claim"))
                .param("executionId", executionId.value())
                .param("businessDate", dailyFeed.businessDate())
                .param("feedId", dailyFeed.feedId())
                .param("maxAttempts", maxAttempts)
                .param("claimExpirySeconds", claimExpirySeconds)
                .update() == 1;
    }

    @Override
    public void finish(DailyFeed dailyFeed, ExecutionId executionId, FeedOutcome outcome) {
        var counts = outcome.counts();
        int updated = jdbc.sql(sql.named("daily-feed/finish"))
                .param("status", outcome.status().name())
                .param("error", ErrorText.fitted(outcome.reason()), Types.VARCHAR)
                .param("staged", counts.staged())
                .param("collisions", counts.collisions())
                .param("sent", counts.sent())
                .param("invalid", counts.invalid())
                .param("businessDate", dailyFeed.businessDate())
                .param("feedId", dailyFeed.feedId())
                .param("executionId", executionId.value())
                .update();
        if (updated == 0) {
            log.warn("{} was taken over by another execution before this one finished; nothing is lost", dailyFeed);
        }
    }

    @Override
    public String describe() {
        return "daily feeds";
    }

    @Override
    public int deleteExpired(int retentionDays, int batchSize) {
        return jdbc.sql(sql.named("daily-feed/delete-finished"))
                .param("retentionDays", retentionDays)
                .param("batchSize", batchSize)
                .update();
    }
}

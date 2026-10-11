package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.config.PublishProperties;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import com.premiumfinance.unpaids.extractor.housekeeping.ExpiredDataPurger;
import com.premiumfinance.unpaids.extractor.ledger.Backlog;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import com.premiumfinance.unpaids.extractor.ledger.Staged;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UNPAID_EVENT_LEDGER in one loan book's database ({@code sql/ledger}). Collisions are kept in the same
 * table with status COLLISION.
 *
 * <p>Staging runs as two set-based statements in one transaction inside the loan book's database, so no
 * unpaid passes through the application on its way into the ledger.
 */
public class JdbcUnpaidLedger implements UnpaidLedger, ExpiredDataPurger {

    private static final int STAGING_TIMEOUT_SECONDS = 300;

    private final LoanBook loanBook;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final SqlLoader sql;
    private final StagingSql staging;
    private final PublishProperties publish;

    public JdbcUnpaidLedger(LoanBook loanBook, DataSource loanBookDatabase, SqlLoader sql, LocalDate goLiveDate,
                            PublishProperties publish) {
        this.loanBook = loanBook;
        this.jdbc = JdbcClient.create(loanBookDatabase);
        this.transaction = new TransactionTemplate(new JdbcTransactionManager(loanBookDatabase));
        this.transaction.setTimeout(STAGING_TIMEOUT_SECONDS);
        this.sql = sql;
        this.staging = new StagingSql(sql, goLiveDate, publish.eventType());
        this.publish = publish;
    }

    @Override
    public LoanBook loanBook() {
        return loanBook;
    }

    @Override
    public Staged stage(UnpaidFeed feed, LocalDate businessDate) {
        var binds = staging.binds(feed, businessDate);
        return transaction.execute(_ -> new Staged(
                jdbc.sql(staging.stage(feed)).params(binds).update(),
                jdbc.sql(staging.collisions(feed)).params(binds).update()));
    }

    @Override
    public List<LedgerEntry> nextPending(UnpaidFeed feed, int limit) {
        return jdbc.sql(sql.named("ledger/next-pending"))
                .param("sourceTable", feed.sourceTable())
                .param("eventType", publish.eventType())
                .param("limit", limit)
                .query(JdbcUnpaidLedger::toEntry)
                .list();
    }

    @Override
    public void markSent(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        jdbc.sql(sql.named("ledger/mark-sent"))
                .param("ids", idsOf(entries))
                .update();
    }

    @Override
    public void markInvalid(LedgerEntry entry, String reason) {
        jdbc.sql(sql.named("ledger/mark-invalid"))
                .param("reason", ErrorText.fitted(reason), Types.VARCHAR)
                .param("id", entry.ledgerId())
                .update();
    }

    @Override
    public void retryLater(List<LedgerEntry> entries, String error) {
        if (entries.isEmpty()) {
            return;
        }
        jdbc.sql(sql.named("ledger/retry-later"))
                .param("error", ErrorText.fitted(error), Types.VARCHAR)
                .param("maxSendAttempts", publish.maxSendAttempts())
                .param("backoffSeconds", publish.retryBackoff().toSeconds())
                .param("maxBackoffSeconds", publish.maxRetryBackoff().toSeconds())
                .param("ids", idsOf(entries))
                .update();
    }

    @Override
    public Backlog backlog(UnpaidFeed feed) {
        return jdbc.sql(sql.named("ledger/backlog"))
                .param("sourceTable", feed.sourceTable())
                .param("eventType", publish.eventType())
                .query((rs, _) -> new Backlog(rs.getInt("pending"), rs.getInt("failed")))
                .single();
    }

    @Override
    public String describe() {
        return loanBook + " ledger";
    }

    @Override
    public int deleteExpired(int retentionDays, int batchSize) {
        return jdbc.sql(sql.named("ledger/delete-published"))
                .param("retentionDays", retentionDays)
                .param("batchSize", batchSize)
                .update();
    }

    private static List<Long> idsOf(List<LedgerEntry> entries) {
        return entries.stream().map(LedgerEntry::ledgerId).toList();
    }

    private static LedgerEntry toEntry(ResultSet rs, int rowNum) throws SQLException {
        return new LedgerEntry(
                rs.getLong("ledger_id"),
                eventIdOf(rs.getBytes("event_id")),
                LoanBook.valueOf(rs.getString("loan_book")),
                UnpaidOrigin.valueOf(rs.getString("origin")),
                rs.getString("source_table"),
                rs.getString("source_ref"),
                rs.getString("loan_number"),
                localDate(rs.getDate("unpaid_date")),
                rs.getString("event_type"),
                localDate(rs.getDate("business_date")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    /** event_id is the RAW(16) MD5 written by stage.sql, shown as a UUID (it becomes the Service Bus MessageId). */
    static String eventIdOf(byte[] raw) {
        if (raw == null || raw.length != 16) {
            throw new IllegalStateException("event_id must be RAW(16)");
        }
        var bytes = ByteBuffer.wrap(raw);
        return new UUID(bytes.getLong(), bytes.getLong()).toString();
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}

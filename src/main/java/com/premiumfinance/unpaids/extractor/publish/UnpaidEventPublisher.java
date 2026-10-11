package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.config.PublishProperties;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedger;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedgers;
import com.premiumfinance.unpaids.extractor.metrics.ExtractorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes a feed's pending ledger entries and keeps the ledger in step with what was sent.
 *
 * <p>An entry is marked SENT only after the broker accepted it. If the job stops in between, the entry
 * is sent again next time and the topic's duplicate detection drops the copy.
 */
@Service
public class UnpaidEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UnpaidEventPublisher.class);
    private static final String TOO_LARGE = "MESSAGE_TOO_LARGE";

    private final UnpaidLedgers ledgers;
    private final UnpaidEventSender sender;
    private final EventValidator validator;
    private final ExtractorMetrics metrics;
    private final PublishProperties properties;

    public UnpaidEventPublisher(UnpaidLedgers ledgers, UnpaidEventSender sender, EventValidator validator,
                                ExtractorMetrics metrics, PublishProperties properties) {
        this.ledgers = ledgers;
        this.sender = sender;
        this.validator = validator;
        this.metrics = metrics;
        this.properties = properties;
    }

    /** Publishes chunk by chunk until nothing is due, or the broker refuses a batch. */
    public PublishResult publishPending(UnpaidFeed feed) {
        UnpaidLedger ledger = ledgers.of(feed.loanBook());
        PublishResult total = PublishResult.NONE;
        List<LedgerEntry> chunk;
        do {
            chunk = ledger.nextPending(feed, properties.chunkSize());
            total = total.plus(publish(ledger, feed, chunk));
        } while (chunk.size() == properties.chunkSize() && !total.sendFailed());

        if (total.sent() > 0 || total.invalid() > 0) {
            log.info("Published {}: {} sent, {} invalid", feed.id(), total.sent(), total.invalid());
        }
        return total;
    }

    private PublishResult publish(UnpaidLedger ledger, UnpaidFeed feed, List<LedgerEntry> chunk) {
        var sendable = new ArrayList<LedgerEntry>(chunk.size());
        int invalid = 0;
        for (LedgerEntry entry : chunk) {
            var problem = validator.problemWith(entry);
            if (problem.isPresent()) {
                markInvalid(ledger, feed, entry, problem.get());
                invalid++;
            } else {
                sendable.add(entry);
            }
        }

        SendReport report = sendable.isEmpty() ? SendReport.NOTHING_TO_SEND : sender.send(sendable);
        record(ledger, feed, report);
        return new PublishResult(report.sent().size(), invalid + report.tooLarge().size(), report.hasFailure());
    }

    private void record(UnpaidLedger ledger, UnpaidFeed feed, SendReport report) {
        if (!report.sent().isEmpty()) {
            ledger.markSent(report.sent());
            metrics.sent(feed, report.sent().size());
        }
        report.tooLarge().forEach(entry -> markInvalid(ledger, feed, entry, TOO_LARGE));

        if (report.hasFailure()) {
            ledger.retryLater(report.notAccepted(), report.failure().toString());
            metrics.sendFailed(feed, report.notAccepted().size());
            log.error("Service Bus did not accept {} event(s) for {}; they will be retried",
                    report.notAccepted().size(), feed.id(), report.failure());
        }
    }

    private void markInvalid(UnpaidLedger ledger, UnpaidFeed feed, LedgerEntry entry, String reason) {
        ledger.markInvalid(entry, reason);
        metrics.invalid(feed);
        log.warn("alert=INVALID_UNPAID feed={} ledgerId={} sourceRef={} reason={}",
                feed.id(), entry.ledgerId(), entry.sourceRef(), reason);
    }
}

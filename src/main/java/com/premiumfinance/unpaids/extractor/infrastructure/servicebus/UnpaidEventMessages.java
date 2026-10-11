package com.premiumfinance.unpaids.extractor.infrastructure.servicebus;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.premiumfinance.unpaids.extractor.config.PublishProperties;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Turns ledger entries into UnpaidRecorded Service Bus messages.
 * <ul>
 *   <li>MessageId is the event id, which is deterministic, so the topic's duplicate detection drops a resend.</li>
 *   <li>SessionId is "PL:&lt;loan number&gt;" or "CL:&lt;loan number&gt;", so a loan's events arrive in order.</li>
 *   <li>Routing fields are also message properties, for subscription filters.</li>
 * </ul>
 */
@Component
public class UnpaidEventMessages {

    static final String CONTENT_TYPE = "application/json";

    private final PublishProperties publish;
    private final JsonMapper json;
    private final Clock clock;

    public UnpaidEventMessages(PublishProperties publish, JsonMapper eventJsonMapper, Clock clock) {
        this.publish = publish;
        this.json = eventJsonMapper;
        this.clock = clock;
    }

    public ServiceBusMessage unpaidRecorded(LedgerEntry entry) {
        Instant occurred = entry.stagedAt() != null ? entry.stagedAt().toInstant() : clock.instant();
        var event = new UnpaidRecordedEvent(
                entry.eventId(),
                entry.eventType(),
                publish.schemaVersion(),
                sourceUri(entry.loanBook()),
                utc(occurred),
                new UnpaidRecordedEvent.Data(
                        entry.loanBook().name(),
                        entry.origin().name(),
                        entry.loanNumber(),
                        entry.unpaidDate().toString(),
                        entry.businessDate().toString(),
                        entry.sourceTable(),
                        entry.sourceRef()));

        return message(event, new Routing(entry.eventId(), entry.loanBook() + ":" + entry.loanNumber(),
                entry.eventType(), entry.loanBook(), entry.origin(), entry.businessDate()));
    }

    private ServiceBusMessage message(Object body, Routing routing) {
        var message = new ServiceBusMessage(BinaryData.fromBytes(json.writeValueAsBytes(body)));
        message.setMessageId(routing.messageId());
        message.setSessionId(routing.sessionId());
        message.setSubject(routing.eventType());
        message.setContentType(CONTENT_TYPE);

        var properties = message.getApplicationProperties();
        properties.put("eventType", routing.eventType());
        properties.put("schemaVersion", publish.schemaVersion());
        properties.put("sourceSystem", routing.loanBook().name());
        properties.put("origin", routing.origin().name());
        properties.put("businessDate", routing.businessDate().toString());
        return message;
    }

    private String sourceUri(LoanBook loanBook) {
        return publish.eventSource() + ":" + loanBook.name().toLowerCase();
    }

    private static String utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toString();
    }

    /** Message headers and properties, as opposed to the JSON body. */
    private record Routing(String messageId, String sessionId, String eventType, LoanBook loanBook,
                           UnpaidOrigin origin, LocalDate businessDate) {
    }
}

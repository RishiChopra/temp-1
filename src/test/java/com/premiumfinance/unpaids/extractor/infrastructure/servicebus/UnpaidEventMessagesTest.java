package com.premiumfinance.unpaids.extractor.infrastructure.servicebus;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** The event contract the case creator depends on. A failure here means a breaking change. */
class UnpaidEventMessagesTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    private final UnpaidEventMessages messages = new UnpaidEventMessages(TestFixtures.publishProperties(), json, clock);

    @Test
    void unpaidRecordedKeepsTheV1ContractAndTheKeysConsumersRelyOn() {
        var entry = TestFixtures.ledgerEntry(1, "PL0001");

        var message = messages.unpaidRecorded(entry);

        assertThat(message.getMessageId()).isEqualTo(entry.eventId());
        assertThat(message.getSessionId()).isEqualTo("PL:PL0001");
        assertThat(message.getSubject()).isEqualTo("UnpaidRecorded");
        assertThat(message.getContentType()).isEqualTo("application/json");
        assertThat(message.getApplicationProperties())
                .containsEntry("eventType", "UnpaidRecorded")
                .containsEntry("sourceSystem", "PL")
                .containsEntry("origin", "BATCH")
                .containsEntry("schemaVersion", "1.0")
                .containsEntry("businessDate", "2026-10-07");

        JsonNode body = json.readTree(message.getBody().toBytes());
        assertThat(body.get("eventId").asString()).isEqualTo(entry.eventId());
        assertThat(body.get("eventType").asString()).isEqualTo("UnpaidRecorded");
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-08T01:30Z");
        assertThat(body.get("source").asString()).isEqualTo("urn:test:unpaids:pl");
        JsonNode data = body.get("data");
        assertThat(data.get("sourceSystem").asString()).isEqualTo("PL");
        assertThat(data.get("origin").asString()).isEqualTo("BATCH");
        assertThat(data.get("loanNumber").asString()).isEqualTo("PL0001");
        assertThat(data.get("unpaidDate").asString()).isEqualTo("2026-10-07");
        assertThat(data.get("businessDate").asString()).isEqualTo("2026-10-07");
        assertThat(data.get("sourceTable").asString()).isEqualTo("UNPAID_DETAILS");
        assertThat(data.get("sourceReference").asString()).isEqualTo("1");
    }
}

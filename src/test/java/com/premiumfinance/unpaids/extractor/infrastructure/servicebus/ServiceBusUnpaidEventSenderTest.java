package com.premiumfinance.unpaids.extractor.infrastructure.servicebus;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusMessageBatch;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceBusUnpaidEventSenderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    private final UnpaidEventMessages messages =
            new UnpaidEventMessages(TestFixtures.publishProperties(), JsonMapper.builder().build(), clock);

    private final ServiceBusSenderClient client = mock(ServiceBusSenderClient.class);
    private final ServiceBusUnpaidEventSender sender = new ServiceBusUnpaidEventSender(client, messages);

    private final LedgerEntry first = TestFixtures.ledgerEntry(1, "PL0001");
    private final LedgerEntry second = TestFixtures.ledgerEntry(2, "PL0002");
    private final LedgerEntry third = TestFixtures.ledgerEntry(3, "PL0003");

    @Test
    void fillsOneBatchWhenEverythingFits() {
        when(client.createMessageBatch()).thenAnswer(_ -> batchHolding(10));

        var report = sender.send(List.of(first, second, third));

        assertThat(report.sent()).containsExactly(first, second, third);
        assertThat(report.hasFailure()).isFalse();
        verify(client, times(1)).sendMessages(any(ServiceBusMessageBatch.class));
    }

    @Test
    void startsANewBatchWhenTheCurrentOneIsFull() {
        when(client.createMessageBatch()).thenAnswer(_ -> batchHolding(2));

        var report = sender.send(List.of(first, second, third));

        assertThat(report.sent()).containsExactly(first, second, third);
        verify(client, times(2)).sendMessages(any(ServiceBusMessageBatch.class));
    }

    @Test
    void aMessageTooLargeForAnEmptyBatchIsReportedNotSent() {
        when(client.createMessageBatch()).thenAnswer(_ -> batchHolding(0));

        var report = sender.send(List.of(first));

        assertThat(report.tooLarge()).containsExactly(first);
        assertThat(report.sent()).isEmpty();
        verify(client, never()).sendMessages(any(ServiceBusMessageBatch.class));
    }

    @Test
    void reportsTheRefusedBatchAndLeavesTheRestUntried() {
        when(client.createMessageBatch()).thenAnswer(_ -> batchHolding(1));
        doThrow(new IllegalStateException("amqp down")).when(client).sendMessages(any(ServiceBusMessageBatch.class));

        var report = sender.send(List.of(first, second, third));

        assertThat(report.hasFailure()).isTrue();
        assertThat(report.sent()).isEmpty();
        assertThat(report.notAccepted()).containsExactly(first);   // second and third were never tried
        assertThat(report.failure()).hasMessage("amqp down");
    }

    private static ServiceBusMessageBatch batchHolding(int capacity) {
        var batch = mock(ServiceBusMessageBatch.class);
        var added = new AtomicInteger();
        when(batch.tryAddMessage(any(ServiceBusMessage.class))).thenAnswer(_ -> added.incrementAndGet() <= capacity);
        return batch;
    }
}

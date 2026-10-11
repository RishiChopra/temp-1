package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedger;
import com.premiumfinance.unpaids.extractor.ledger.UnpaidLedgers;
import com.premiumfinance.unpaids.extractor.metrics.ExtractorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnpaidEventPublisherTest {

    private static final int CHUNK_SIZE = 3;

    private final UnpaidFeed feed = TestFixtures.plBatchFeed();
    private final LedgerEntry first = TestFixtures.ledgerEntry(1, "PL0001");
    private final LedgerEntry badLoanNumber = TestFixtures.ledgerEntry(2, "pl 0003");
    private final LedgerEntry second = TestFixtures.ledgerEntry(3, "PL0002");

    private UnpaidLedger ledger;
    private UnpaidEventSender sender;
    private UnpaidEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ledger = mock(UnpaidLedger.class);
        when(ledger.loanBook()).thenReturn(LoanBook.PL);
        sender = mock(UnpaidEventSender.class);
        var properties = TestFixtures.publishProperties(CHUNK_SIZE);
        publisher = new UnpaidEventPublisher(new UnpaidLedgers(List.of(ledger)), sender, new EventValidator(properties),
                new ExtractorMetrics(new SimpleMeterRegistry()), properties);
    }

    @Test
    void sendsValidUnpaidsAndMarksBadOnesInvalid() {
        when(ledger.nextPending(feed, CHUNK_SIZE)).thenReturn(List.of(first, badLoanNumber, second), List.of());
        when(sender.send(List.of(first, second))).thenReturn(SendReport.accepted(List.of(first, second), List.of()));

        var result = publisher.publishPending(feed);

        assertThat(result).isEqualTo(new PublishResult(2, 1, false));
        verify(ledger).markSent(List.of(first, second));
        verify(ledger).markInvalid(eq(badLoanNumber), startsWith("LOAN_NUMBER_FORMAT"));
    }

    @Test
    void anUnpaidTooLargeToSendIsMarkedInvalid() {
        when(ledger.nextPending(feed, CHUNK_SIZE)).thenReturn(List.of(first));
        when(sender.send(List.of(first))).thenReturn(SendReport.accepted(List.of(), List.of(first)));

        var result = publisher.publishPending(feed);

        assertThat(result.invalid()).isEqualTo(1);
        verify(ledger).markInvalid(first, "MESSAGE_TOO_LARGE");
    }

    @Test
    void whatTheBrokerRefusedIsSetToRetryAndPublishingStops() {
        when(ledger.nextPending(feed, CHUNK_SIZE)).thenReturn(List.of(first, second, TestFixtures.ledgerEntry(4, "PL0004")));
        when(sender.send(any())).thenReturn(
                SendReport.refused(List.of(first), List.of(), List.of(second), new IllegalStateException("amqp down")));

        var result = publisher.publishPending(feed);

        assertThat(result).isEqualTo(new PublishResult(1, 0, true));
        verify(ledger).markSent(List.of(first));
        verify(ledger).retryLater(eq(List.of(second)), anyString());
        verify(ledger, times(1)).nextPending(feed, CHUNK_SIZE); // a full chunk, but no second round trip
    }

    @Test
    void nothingPendingMeansNoTrafficToTheBroker() {
        when(ledger.nextPending(feed, CHUNK_SIZE)).thenReturn(List.of());

        assertThat(publisher.publishPending(feed)).isEqualTo(PublishResult.NONE);
        verify(sender, never()).send(any());
    }

    @Test
    void keepsReadingWhileChunksComeBackFull() {
        var fullChunk = List.of(first, second, TestFixtures.ledgerEntry(4, "PL0004"));
        when(ledger.nextPending(eq(feed), anyInt())).thenReturn(fullChunk, List.of());
        when(sender.send(fullChunk)).thenReturn(SendReport.accepted(fullChunk, List.of()));

        assertThat(publisher.publishPending(feed).sent()).isEqualTo(3);
        verify(ledger, times(2)).nextPending(feed, CHUNK_SIZE);
    }
}

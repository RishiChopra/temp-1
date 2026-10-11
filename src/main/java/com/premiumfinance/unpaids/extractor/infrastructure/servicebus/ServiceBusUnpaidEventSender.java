package com.premiumfinance.unpaids.extractor.infrastructure.servicebus;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusMessageBatch;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import com.premiumfinance.unpaids.extractor.publish.SendReport;
import com.premiumfinance.unpaids.extractor.publish.UnpaidEventSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends unpaid events to the Service Bus topic in as few batches as the size limit allows (the SDK
 * enforces the limit). Retries of transient failures are left to the SDK's retry policy.
 */
public class ServiceBusUnpaidEventSender implements UnpaidEventSender {

    private final ServiceBusSenderClient client;
    private final UnpaidEventMessages messages;

    public ServiceBusUnpaidEventSender(ServiceBusSenderClient client, UnpaidEventMessages messages) {
        this.client = client;
        this.messages = messages;
    }

    @Override
    public SendReport send(List<LedgerEntry> entries) {
        var batches = new Batches();
        try {
            entries.forEach(batches::add);
            batches.sendCurrent();
            return SendReport.accepted(batches.sent, batches.tooLarge);
        } catch (RuntimeException e) {
            return SendReport.refused(batches.sent, batches.tooLarge, batches.inCurrent, e);
        }
    }

    /** Fills a batch, sends it when the next message no longer fits, and starts another. */
    private final class Batches {

        private final List<LedgerEntry> sent = new ArrayList<>();
        private final List<LedgerEntry> tooLarge = new ArrayList<>();
        private List<LedgerEntry> inCurrent = new ArrayList<>();
        private ServiceBusMessageBatch current;

        void add(LedgerEntry entry) {
            ServiceBusMessage message = messages.unpaidRecorded(entry);
            if (tryAdd(entry, message)) {
                return;
            }
            if (inCurrent.isEmpty()) {
                tooLarge.add(entry); // does not fit even in an empty batch
                return;
            }
            sendCurrent();
            if (!tryAdd(entry, message)) {
                tooLarge.add(entry);
            }
        }

        void sendCurrent() {
            if (inCurrent.isEmpty()) {
                return;
            }
            client.sendMessages(current);
            sent.addAll(inCurrent);
            inCurrent = new ArrayList<>();
            current = null;
        }

        private boolean tryAdd(LedgerEntry entry, ServiceBusMessage message) {
            if (current == null) {
                current = client.createMessageBatch();
            }
            if (!current.tryAddMessage(message)) {
                return false;
            }
            inCurrent.add(entry);
            return true;
        }
    }
}

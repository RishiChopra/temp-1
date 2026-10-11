package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;

import java.util.List;

/** Delivers unpaid events to consumers (the Service Bus topic in production). */
public interface UnpaidEventSender {

    /**
     * Sends an UnpaidRecorded event for each entry, in order. Stops at the first batch the broker does
     * not accept; entries after it are left untried. Never throws for a broker failure, it reports it.
     */
    SendReport send(List<LedgerEntry> entries);
}

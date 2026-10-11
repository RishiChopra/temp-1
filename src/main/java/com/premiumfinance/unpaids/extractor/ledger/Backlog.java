package com.premiumfinance.unpaids.extractor.ledger;

/**
 * What is left in the ledger for a feed after publishing.
 *
 * @param pending entries still to send (due now or waiting to retry)
 * @param failed  entries that used up their send attempts
 */
public record Backlog(int pending, int failed) {

    public boolean isEmpty() {
        return pending == 0 && failed == 0;
    }
}

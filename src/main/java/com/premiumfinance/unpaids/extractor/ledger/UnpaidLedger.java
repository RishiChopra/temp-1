package com.premiumfinance.unpaids.extractor.ledger;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;

import java.time.LocalDate;
import java.util.List;

/**
 * A loan book's ledger of unpaid events (UNPAID_EVENT_LEDGER in the PL or CL database): what has been
 * staged, what has been sent and what could not be. It is what makes publishing exactly-once from the
 * consumer's point of view: an unpaid is staged once, and marked sent only after Service Bus accepted it.
 */
public interface UnpaidLedger {

    LoanBook loanBook();

    /**
     * Copies the feed's new unpaids into the ledger. Any whose loan and unpaid date are already taken
     * (a batch and a manual unpaid for the same day is a mistake) are kept as COLLISION and never sent.
     */
    Staged stage(UnpaidFeed feed, LocalDate businessDate);

    /** The next entries due to be sent, oldest loan first, skipping any waiting to retry. */
    List<LedgerEntry> nextPending(UnpaidFeed feed, int limit);

    void markSent(List<LedgerEntry> entries);

    void markInvalid(LedgerEntry entry, String reason);

    /** Service Bus did not accept these: retry later with back-off, or park them as failed after the last attempt. */
    void retryLater(List<LedgerEntry> entries, String error);

    Backlog backlog(UnpaidFeed feed);
}

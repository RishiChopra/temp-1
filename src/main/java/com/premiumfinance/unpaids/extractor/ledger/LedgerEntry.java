package com.premiumfinance.unpaids.extractor.ledger;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** An unpaid staged in a loan book's ledger, waiting to be published. */
public record LedgerEntry(
        long ledgerId,
        String eventId,
        LoanBook loanBook,
        UnpaidOrigin origin,
        String sourceTable,
        String sourceRef,
        String loanNumber,
        LocalDate unpaidDate,
        String eventType,
        LocalDate businessDate,
        OffsetDateTime stagedAt) {
}

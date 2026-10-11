package com.premiumfinance.unpaids.extractor.ledger;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the ledger of a loan book. */
@Component
public class UnpaidLedgers {

    private final Map<LoanBook, UnpaidLedger> byLoanBook = new EnumMap<>(LoanBook.class);

    public UnpaidLedgers(List<UnpaidLedger> ledgers) {
        for (UnpaidLedger ledger : ledgers) {
            if (byLoanBook.putIfAbsent(ledger.loanBook(), ledger) != null) {
                throw new IllegalStateException("More than one ledger for " + ledger.loanBook());
            }
        }
    }

    public UnpaidLedger of(LoanBook loanBook) {
        UnpaidLedger ledger = byLoanBook.get(loanBook);
        if (ledger == null) {
            throw new IllegalStateException("No ledger for " + loanBook);
        }
        return ledger;
    }
}

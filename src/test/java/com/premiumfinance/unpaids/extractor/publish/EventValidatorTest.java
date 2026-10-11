package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventValidatorTest {

    private final EventValidator validator = new EventValidator(TestFixtures.publishProperties());

    @Test
    void acceptsAWellFormedUnpaid() {
        assertThat(validator.problemWith(TestFixtures.ledgerEntry(1, "PL0001"))).isEmpty();
    }

    @Test
    void rejectsAMissingOrMalformedLoanNumber() {
        assertThat(validator.problemWith(TestFixtures.ledgerEntry(1, null))).contains("LOAN_NUMBER_MISSING");
        assertThat(validator.problemWith(TestFixtures.ledgerEntry(1, "  "))).contains("LOAN_NUMBER_MISSING");
        assertThat(validator.problemWith(TestFixtures.ledgerEntry(1, "pl 0003")).orElseThrow()).startsWith("LOAN_NUMBER_FORMAT");
    }

    @Test
    void rejectsAMissingUnpaidDate() {
        var e = TestFixtures.ledgerEntry(1, "PL0001");
        var noDate = new LedgerEntry(e.ledgerId(), e.eventId(), e.loanBook(), e.origin(), e.sourceTable(), e.sourceRef(),
                e.loanNumber(), null, e.eventType(), e.businessDate(), e.stagedAt());

        assertThat(validator.problemWith(noDate)).contains("UNPAID_DATE_MISSING");
    }
}

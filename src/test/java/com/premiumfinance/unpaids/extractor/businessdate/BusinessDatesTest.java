package com.premiumfinance.unpaids.extractor.businessdate;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDatesTest {

    @Test
    void eachLoanBookKeepsItsOwnBusinessDate() {
        BusinessCalendar calendar = loanBook -> loanBook == LoanBook.PL ? LocalDate.of(2026, 12, 15) : LocalDate.of(2026, 12, 14);

        var dates = BusinessDates.readFrom(calendar);

        assertThat(dates.of(LoanBook.PL)).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(dates.of(LoanBook.CL)).isEqualTo(LocalDate.of(2026, 12, 14));
    }
}

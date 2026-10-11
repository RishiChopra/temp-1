package com.premiumfinance.unpaids.extractor.businessdate;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Each loan book's business date, read once at the start of an execution so the whole execution works on
 * the same dates even if end of day happens while it runs.
 */
public record BusinessDates(Map<LoanBook, LocalDate> byLoanBook) {

    public BusinessDates {
        byLoanBook = Map.copyOf(byLoanBook);
    }

    public static BusinessDates readFrom(BusinessCalendar calendar) {
        var dates = new EnumMap<LoanBook, LocalDate>(LoanBook.class);
        for (LoanBook loanBook : LoanBook.values()) {
            dates.put(loanBook, calendar.currentBusinessDate(loanBook));
        }
        return new BusinessDates(dates);
    }

    public LocalDate of(LoanBook loanBook) {
        return byLoanBook.get(loanBook);
    }

    @Override
    public String toString() {
        return new EnumMap<>(byLoanBook).toString();
    }
}

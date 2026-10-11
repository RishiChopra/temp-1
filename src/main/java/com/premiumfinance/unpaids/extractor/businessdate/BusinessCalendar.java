package com.premiumfinance.unpaids.extractor.businessdate;

import com.premiumfinance.unpaids.extractor.feed.LoanBook;

import java.time.LocalDate;

/**
 * The business date a loan book is currently processing. This is the monolith's date, not the wall
 * clock: test environments move it on, and PL and CL can be on different days. To test another business
 * date, move eodc_date in the monolith.
 */
public interface BusinessCalendar {

    LocalDate currentBusinessDate(LoanBook loanBook);
}

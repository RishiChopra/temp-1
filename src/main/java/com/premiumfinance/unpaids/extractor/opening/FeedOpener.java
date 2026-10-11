package com.premiumfinance.unpaids.extractor.opening;

import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import com.premiumfinance.unpaids.extractor.feed.UnpaidOrigin;

import java.time.LocalDate;

/** Decides whether a feed of one origin has work for the loan book's business date, and opens it. */
public interface FeedOpener {

    UnpaidOrigin origin();

    void openDueDays(UnpaidFeed feed, LocalDate businessDate);
}

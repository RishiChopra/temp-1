package com.premiumfinance.unpaids.extractor.feed;

/**
 * The monolith's two loan books. Each has its own database holding its unpaids, its transactions and
 * the extractor's ledger of published events.
 */
public enum LoanBook {

    /** Personal loans. */
    PL,

    /** Commercial loans. */
    CL
}

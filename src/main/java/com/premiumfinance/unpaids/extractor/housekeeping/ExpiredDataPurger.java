package com.premiumfinance.unpaids.extractor.housekeeping;

/** A table the extractor writes to that needs old rows deleting. */
public interface ExpiredDataPurger {

    /** For the log, e.g. "PL ledger". */
    String describe();

    /** Deletes up to {@code batchSize} finished rows older than {@code retentionDays}. @return rows deleted */
    int deleteExpired(int retentionDays, int batchSize);
}

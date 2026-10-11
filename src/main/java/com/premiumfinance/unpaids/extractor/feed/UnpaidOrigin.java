package com.premiumfinance.unpaids.extractor.feed;

/** How an unpaid entered the monolith. This decides when the extractor picks it up. */
public enum UnpaidOrigin {

    /**
     * Written by the daily unpaids run (I_SCHEDULED_JOBS job 133 for PL, 91 for CL). Published once that
     * run has finished.
     */
    BATCH(true),

    /** Keyed in by staff and approved during the day. Published on every execution, all day. */
    MANUAL(false);

    private final boolean writtenByUnpaidsRun;

    UnpaidOrigin(boolean writtenByUnpaidsRun) {
        this.writtenByUnpaidsRun = writtenByUnpaidsRun;
    }

    /** Feeds of this origin wait for the unpaids run in I_SCHEDULED_JOBS before they are opened. */
    public boolean followsUnpaidsRun() {
        return writtenByUnpaidsRun;
    }
}

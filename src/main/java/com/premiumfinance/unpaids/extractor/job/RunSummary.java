package com.premiumfinance.unpaids.extractor.job;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessDates;
import com.premiumfinance.unpaids.extractor.dailyfeed.ExecutionId;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.Done;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.NeedsAttention;
import com.premiumfinance.unpaids.extractor.dailyfeed.FeedOutcome.RetryLater;

import java.time.Duration;
import java.util.List;

/**
 * What one execution did.
 *
 * @param businessDates    each loan book's business date for this execution
 * @param allFeedsOpened   false if opening any feed failed
 * @param skipped          daily feeds another execution had, or that had nothing to do
 */
public record RunSummary(ExecutionId executionId, BusinessDates businessDates, boolean allFeedsOpened,
                         int done, int retrying, int needingAttention, int skipped, Duration duration) {

    public static RunSummary of(ExecutionId executionId, BusinessDates businessDates, boolean allFeedsOpened,
                                List<FeedOutcome> outcomes, int skipped, Duration duration) {
        int done = 0;
        int retrying = 0;
        int needingAttention = 0;
        for (FeedOutcome outcome : outcomes) {
            switch (outcome) {
                case Done _ -> done++;
                case RetryLater _ -> retrying++;
                case NeedsAttention _ -> needingAttention++;
            }
        }
        return new RunSummary(executionId, businessDates, allFeedsOpened, done, retrying, needingAttention,
                skipped, duration);
    }

    /** Everything picked up was published, or there was nothing to do. */
    public boolean successful() {
        return allFeedsOpened && retrying == 0 && needingAttention == 0;
    }
}

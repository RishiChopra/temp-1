package com.premiumfinance.unpaids.extractor.feed;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One place unpaids come from, e.g. "PL batch unpaids" or "CL manual unpaids". Adding another manual
 * table means one more entry under {@code extractor.feeds} and one more query file, with no Java change.
 *
 * <p>The feed's query must return SOURCE_REF (unique within the source table), LOAN_NUMBER and
 * UNPAID_DATE. It can use :windowStart, :businessDate, :goLiveDate and any of {@link #queryParams()}.
 *
 * @param id            stable id, e.g. pl-batch; part of the daily feed key, so never rename it once live
 * @param loanBook      which database the feed reads, and keeps its ledger in
 * @param origin        decides when the feed runs: batch feeds after the unpaids run, manual feeds all day
 * @param sourceTable   logical name of the source table, stored on every ledger entry; never rename once live
 * @param query         resource location of the feed's query, e.g. classpath:sql/feeds/pl/unpaid-details.sql
 * @param lookbackDays  how far back the query looks (floored at go-live) to catch late and re-run rows
 * @param unpaidsJobId  feeds that follow the unpaids run only: its job id in I_SCHEDULED_JOBS (133 = PL, 91 = CL)
 * @param queryParams   extra binds for the query; a list binds as IN (...)
 */
public record UnpaidFeed(
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,50}") String id,
        @DefaultValue("true") boolean enabled,
        @NotNull LoanBook loanBook,
        @NotNull UnpaidOrigin origin,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,60}") String sourceTable,
        @NotBlank String query,
        @Min(0) int lookbackDays,
        Integer unpaidsJobId,
        Map<String, List<String>> queryParams) {

    public UnpaidFeed {
        queryParams = queryParams == null ? Map.of() : Map.copyOf(queryParams);
        if (origin != null && origin.followsUnpaidsRun() && unpaidsJobId == null) {
            throw new IllegalArgumentException("Feed '" + id + "' follows the unpaids run, so it needs unpaids-job-id");
        }
    }

    /** First unpaid date the feed's query looks at: the lookback window, but never before go-live. */
    public LocalDate windowStart(LocalDate businessDate, LocalDate goLiveDate) {
        return latest(businessDate.minusDays(lookbackDays), goLiveDate);
    }

    static LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}

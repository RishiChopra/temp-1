package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.opening.FinishedRun;
import com.premiumfinance.unpaids.extractor.opening.FinishedUnpaidsRuns;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Optional;

/** Reads I_SCHEDULED_JOBS in the main database ({@code sql/main/latest-finished-unpaids-run.sql}). */
public class JdbcFinishedUnpaidsRuns implements FinishedUnpaidsRuns {

    private final JdbcClient jdbc;
    private final SqlLoader sql;

    public JdbcFinishedUnpaidsRuns(DataSource mainDatabase, SqlLoader sql) {
        this.jdbc = JdbcClient.create(mainDatabase);
        this.sql = sql;
    }

    @Override
    public Optional<FinishedRun> latest(int jobId, LocalDate notBefore) {
        return jdbc.sql(sql.named("main/latest-finished-unpaids-run"))
                .param("jobId", jobId)
                .param("notBefore", notBefore)
                .query((rs, _) -> new FinishedRun(rs.getString("run_ref"), rs.getTimestamp("end_time").toLocalDateTime()))
                .optional();
    }
}

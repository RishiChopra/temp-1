package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessCalendar;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads the business date the way the monolith does: eodc_date in the loan book's own database
 * ({@code sql/business-date/eodc-date.sql}). So the extractor follows the PL and CL books wherever they
 * are, including test environments that move the date on.
 */
public class EodcBusinessCalendar implements BusinessCalendar {

    private final Map<LoanBook, JdbcClient> databases = new EnumMap<>(LoanBook.class);
    private final SqlLoader sql;

    public EodcBusinessCalendar(Map<LoanBook, DataSource> loanBookDatabases, SqlLoader sql) {
        loanBookDatabases.forEach((loanBook, dataSource) -> databases.put(loanBook, JdbcClient.create(dataSource)));
        this.sql = sql;
    }

    @Override
    public LocalDate currentBusinessDate(LoanBook loanBook) {
        LocalDate businessDate = databases.get(loanBook)
                .sql(sql.named("business-date/eodc-date"))
                .query((rs, _) -> rs.getDate("business_date").toLocalDate())
                .single();
        if (businessDate == null) {
            throw new IllegalStateException("eodc_date returned nothing for " + loanBook);
        }
        return businessDate;
    }
}

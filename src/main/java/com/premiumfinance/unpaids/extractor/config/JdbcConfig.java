package com.premiumfinance.unpaids.extractor.config;

import com.premiumfinance.unpaids.extractor.businessdate.BusinessCalendar;
import com.premiumfinance.unpaids.extractor.feed.LoanBook;
import com.premiumfinance.unpaids.extractor.infrastructure.jdbc.EodcBusinessCalendar;
import com.premiumfinance.unpaids.extractor.infrastructure.jdbc.JdbcDailyFeedStore;
import com.premiumfinance.unpaids.extractor.infrastructure.jdbc.JdbcFinishedUnpaidsRuns;
import com.premiumfinance.unpaids.extractor.infrastructure.jdbc.JdbcUnpaidLedger;
import com.premiumfinance.unpaids.extractor.infrastructure.jdbc.SqlLoader;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The three monolith databases and the adapters that use them:
 * <ul>
 *   <li><b>main</b>: I_SCHEDULED_JOBS (read) and UNPAID_DAILY_FEED (read/write);</li>
 *   <li><b>PL</b> and <b>CL</b>: the loan book's business date (eodc_date), unpaids and transactions (read),
 *       plus its ledger and exception table (read/write).</li>
 * </ul>
 * Pool settings (jdbc-url, username, password, maximum-pool-size, ...) bind onto Hikari from
 * {@code extractor.datasources.*}.
 */
@Configuration(proxyBeanMethods = false)
public class JdbcConfig {

    @Bean(destroyMethod = "close")
    @Primary
    @ConfigurationProperties("extractor.datasources.main")
    public HikariDataSource mainDataSource() {
        return new HikariDataSource();
    }

    @Bean(destroyMethod = "close")
    @ConfigurationProperties("extractor.datasources.pl")
    public HikariDataSource plDataSource() {
        return new HikariDataSource();
    }

    @Bean(destroyMethod = "close")
    @ConfigurationProperties("extractor.datasources.cl")
    public HikariDataSource clDataSource() {
        return new HikariDataSource();
    }

    @Bean
    public JdbcDailyFeedStore dailyFeedStore(@Qualifier("mainDataSource") DataSource main, SqlLoader sql,
                                             ExtractorProperties properties) {
        return new JdbcDailyFeedStore(main, sql, properties.goLiveDate(), properties.claimExpiresAfter(),
                properties.maxAttempts());
    }

    @Bean
    public JdbcFinishedUnpaidsRuns finishedUnpaidsRuns(@Qualifier("mainDataSource") DataSource main, SqlLoader sql) {
        return new JdbcFinishedUnpaidsRuns(main, sql);
    }

    @Bean
    public JdbcUnpaidLedger plLedger(@Qualifier("plDataSource") DataSource pl, SqlLoader sql,
                                     ExtractorProperties extractor, PublishProperties publish) {
        return new JdbcUnpaidLedger(LoanBook.PL, pl, sql, extractor.goLiveDate(), publish);
    }

    @Bean
    public JdbcUnpaidLedger clLedger(@Qualifier("clDataSource") DataSource cl, SqlLoader sql,
                                     ExtractorProperties extractor, PublishProperties publish) {
        return new JdbcUnpaidLedger(LoanBook.CL, cl, sql, extractor.goLiveDate(), publish);
    }

    /** The monolith's business date (eodc_date) in each loan book database. */
    @Bean
    public BusinessCalendar businessCalendar(@Qualifier("plDataSource") DataSource pl,
                                             @Qualifier("clDataSource") DataSource cl, SqlLoader sql) {
        return new EodcBusinessCalendar(Map.of(LoanBook.PL, pl, LoanBook.CL, cl), sql);
    }
}

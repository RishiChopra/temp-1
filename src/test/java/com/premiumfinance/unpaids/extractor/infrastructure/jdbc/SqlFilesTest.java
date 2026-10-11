package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Every SQL file the adapters load by name exists, is not empty and has no trailing ';'. */
class SqlFilesTest {

    private final SqlLoader sql = new SqlLoader(new DefaultResourceLoader());

    @ParameterizedTest
    @ValueSource(strings = {
            "daily-feed/open", "daily-feed/close-earlier-manual", "daily-feed/find-open", "daily-feed/claim",
            "daily-feed/finish", "daily-feed/delete-finished", "daily-feed/business-date-of-run",
            "ledger/stage", "ledger/collisions", "ledger/next-pending", "ledger/mark-sent", "ledger/mark-invalid",
            "ledger/retry-later", "ledger/backlog", "ledger/delete-published",
            "main/latest-finished-unpaids-run", "business-date/eodc-date"})
    void namedSqlFileLoads(String name) {
        assertThat(sql.named(name)).isNotBlank().doesNotEndWith(";");
    }
}

package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import com.premiumfinance.unpaids.extractor.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagingSqlTest {

    private final SqlLoader sql = new SqlLoader(new DefaultResourceLoader());
    private final StagingSql staging = new StagingSql(sql, TestFixtures.GO_LIVE, "UnpaidRecorded");

    @Test
    void standardBindsWinOverTheFeedsOwnAndListsStayLists() {
        var binds = staging.binds(TestFixtures.plManualFeed(), LocalDate.of(2026, 10, 8));

        assertThat(binds)
                .containsEntry("businessDate", LocalDate.of(2026, 10, 8))
                .containsEntry("manualTxnTypes", List.of("MUNP", "MUNP2"))
                .containsEntry("approvedStatus", "APPROVED")
                .containsEntry("windowStart", LocalDate.of(2026, 9, 8))
                .containsEntry("loanBook", "PL")
                .containsEntry("eventType", "UnpaidRecorded")
                .containsKeys("origin", "sourceTable", "goLiveDate");
    }

    @Test
    void feedQueryGoesOnItsOwnLinesSoATrailingCommentIsHarmless() {
        String text = StagingSql.withFeedQuery("SELECT * FROM ( {{FEED_QUERY}} ) f", "SELECT 1 FROM dual -- note");

        assertThat(text).isEqualTo("SELECT * FROM ( \nSELECT 1 FROM dual -- note\n ) f");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pl/unpaid-details", "pl/manual-unpaids", "cl/unpaid-details", "cl/manual-unpaids"})
    void everyFeedQueryFitsBothStatements(String query) {
        String feedQuery = sql.load("classpath:sql/feeds/" + query + ".sql");
        for (String template : List.of("ledger/stage", "ledger/collisions")) {
            assertThat(StagingSql.withFeedQuery(sql.named(template), feedQuery))
                    .doesNotContain(StagingSql.FEED_QUERY)
                    .doesNotEndWith(";");
        }
    }

    @Test
    void aTemplateWithoutThePlaceholderIsRejected() {
        assertThatThrownBy(() -> StagingSql.withFeedQuery("SELECT 1 FROM dual", "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventIdIsShownAsAUuid() {
        byte[] raw = new byte[16];
        raw[15] = 1;

        assertThat(JdbcUnpaidLedger.eventIdOf(raw)).isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}

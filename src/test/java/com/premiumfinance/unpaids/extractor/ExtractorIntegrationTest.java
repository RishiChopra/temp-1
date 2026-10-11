package com.premiumfinance.unpaids.extractor;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusMessageBatch;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.premiumfinance.unpaids.extractor.job.ExtractionJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The whole job against a real Oracle (production DDL plus the local seed data), with the Service Bus
 * client replaced by a mock that records what is sent. Run with {@code ./gradlew integrationTest} (needs Docker).
 */
@Tag("integration")
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(properties = "extractor.run-on-startup=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExtractorIntegrationTest {

    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withCopyFileToContainer(MountableFile.forHostPath("db"), "/opt/db")
            .withCopyFileToContainer(MountableFile.forHostPath("local/oracle-init"), "/container-entrypoint-initdb.d")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void oracle(DynamicPropertyRegistry registry) {
        String url = "jdbc:oracle:thin:@//" + ORACLE.getHost() + ":" + ORACLE.getMappedPort(1521) + "/FREEPDB1";
        for (String database : List.of("main", "pl", "cl")) {
            registry.add("extractor.datasources." + database + ".jdbc-url", () -> url);
        }
    }

    /** Messages "sent" to Service Bus, shared across the ordered test methods. */
    static final List<ServiceBusMessage> SENT = new CopyOnWriteArrayList<>();
    static final Map<ServiceBusMessageBatch, List<ServiceBusMessage>> BATCH_CONTENTS = new ConcurrentHashMap<>();

    @MockitoBean
    ServiceBusSenderClient serviceBusClient;

    @Autowired
    ExtractionJob job;

    @Autowired
    @Qualifier("mainDataSource")
    DataSource mainDatabase;

    @Autowired
    @Qualifier("plDataSource")
    DataSource plDatabase;

    @BeforeEach
    void captureSends() {
        // Mock beans are reset after each test, so the stubbing is applied again here.
        when(serviceBusClient.createMessageBatch()).thenAnswer(_ -> capturingBatch());
        doAnswer(inv -> {
            SENT.addAll(BATCH_CONTENTS.remove(inv.<ServiceBusMessageBatch>getArgument(0)));
            return null;
        }).when(serviceBusClient).sendMessages(any(ServiceBusMessageBatch.class));
    }

    @Test
    @Order(1)
    void firstRunPublishesBatchAndManualUnpaidsAndQuarantinesTheCollision() {
        var summary = job.run();

        assertThat(summary.successful()).isTrue();
        assertThat(summary.done()).isEqualTo(4);   // pl-batch, cl-batch, pl-manual and cl-manual for their days

        assertThat(SENT).hasSize(6);
        assertThat(SENT).extracting(ServiceBusMessage::getSessionId)
                .containsExactlyInAnyOrder("PL:PL0001", "PL:PL0002", "PL:PL0100", "CL:CL0001", "CL:CL0002", "CL:CL0200")
                .doesNotContain("PL:PL0101", "PL:PL0102", "PL:PL0099");
        assertThat(SENT).extracting(ServiceBusMessage::getMessageId).doesNotHaveDuplicates();

        var pl = JdbcClient.create(plDatabase);
        assertThat(count(pl, "SELECT COUNT(*) FROM unpaid_event_ledger WHERE status = 'SENT'")).isEqualTo(3);
        assertThat(count(pl, "SELECT COUNT(*) FROM unpaid_event_ledger WHERE status = 'INVALID'")).isEqualTo(1);
        assertThat(pl.sql("SELECT last_error FROM unpaid_event_ledger WHERE status = 'COLLISION' AND loan_number = 'PL0001'")
                .query(String.class).single()).isEqualTo("NATURAL_KEY_COLLISION");

        var main = JdbcClient.create(mainDatabase);
        assertThat(count(main, "SELECT COUNT(*) FROM unpaid_daily_feed WHERE status = 'DONE'")).isEqualTo(4);
    }

    @Test
    @Order(2)
    void runningAgainPublishesNothingNew() {
        int before = SENT.size();

        var summary = job.run();

        assertThat(summary.successful()).isTrue();
        assertThat(SENT).hasSize(before);   // nothing re-published, no duplicate collision rows
        assertThat(count(JdbcClient.create(plDatabase), "SELECT COUNT(*) FROM unpaid_event_ledger WHERE status = 'COLLISION'"))
                .isEqualTo(1);
    }

    private static int count(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private static ServiceBusMessageBatch capturingBatch() {
        var batch = mock(ServiceBusMessageBatch.class);
        var pending = new CopyOnWriteArrayList<ServiceBusMessage>();
        when(batch.tryAddMessage(any(ServiceBusMessage.class))).thenAnswer(inv -> pending.add(inv.getArgument(0)));
        when(batch.getCount()).thenAnswer(_ -> pending.size());
        BATCH_CONTENTS.put(batch, pending);
        return batch;
    }
}

package com.premiumfinance.unpaids.extractor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Publishes unpaid events for the PL and CL loan books to Azure Service Bus.
 *
 * <p>Runs as an Azure Container Apps Job: each execution does the work that is due and exits with a code
 * that tells ACA whether it succeeded.
 *
 * <p>Package layout (ports and adapters):
 * <ul>
 *   <li>{@code feed}, {@code dailyfeed}, {@code ledger}, {@code publish}: the domain and its ports
 *       (interfaces such as {@code DailyFeedStore}, {@code UnpaidLedger}, {@code UnpaidEventSender});</li>
 *   <li>{@code opening}, {@code processing}, {@code housekeeping}, {@code job}: the use cases;</li>
 *   <li>{@code infrastructure.jdbc}, {@code infrastructure.servicebus}: the adapters behind the ports;</li>
 *   <li>{@code config}: settings and wiring.</li>
 * </ul>
 * Nothing outside {@code infrastructure} and {@code config} knows about JDBC or the Service Bus SDK.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ExtractorApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ExtractorApplication.class, args)));
    }
}

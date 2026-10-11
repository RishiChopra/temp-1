package com.premiumfinance.unpaids.extractor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * The Service Bus topic and how to reach it ({@code extractor.service-bus.*}). Set exactly one of
 * connection string (emulator) or fully qualified namespace (Azure, with managed identity).
 *
 * @param connectionString        emulator / local only; empty in Azure
 * @param fullyQualifiedNamespace e.g. sb-premiumfinance-dev.servicebus.windows.net
 * @param managedIdentityClientId client id of the user-assigned identity (optional; AZURE_CLIENT_ID works too)
 * @param transport               AMQP (port 5671), or AMQP_WEB_SOCKETS (443) for networks that block 5671
 */
@Validated
@ConfigurationProperties("extractor.service-bus")
public record ServiceBusProperties(
        String connectionString,
        String fullyQualifiedNamespace,
        String managedIdentityClientId,
        @DefaultValue("AMQP") Transport transport,
        @NotBlank String topic,
        @Valid @NotNull Retry retry) {

    public enum Transport { AMQP, AMQP_WEB_SOCKETS }

    public record Retry(@Min(0) int maxRetries, @NotNull Duration delay, @NotNull Duration maxDelay,
                        @NotNull Duration tryTimeout) {
    }

    public ServiceBusProperties {
        if (hasText(connectionString) == hasText(fullyQualifiedNamespace)) {
            throw new IllegalArgumentException("Set exactly one of extractor.service-bus.connection-string "
                    + "or extractor.service-bus.fully-qualified-namespace");
        }
    }

    public boolean usesConnectionString() {
        return hasText(connectionString);
    }

    public boolean hasManagedIdentityClientId() {
        return hasText(managedIdentityClientId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

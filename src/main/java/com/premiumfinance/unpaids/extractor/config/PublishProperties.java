package com.premiumfinance.unpaids.extractor.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How unpaid events are published ({@code extractor.publish.*}).
 *
 * @param chunkSize                ledger entries read per round trip (Oracle allows at most 1000 in an IN list)
 * @param maxSendAttempts          send attempts per event before it is parked as FAILED
 * @param retryBackoff             wait before the first resend; doubles each attempt
 * @param maxRetryBackoff          longest wait between resends
 * @param eventType                type of the event each unpaid becomes
 * @param schemaVersion            event contract version
 * @param eventSource              prefix of the event's "source", e.g. urn:premiumfinance:monolith:unpaids
 * @param loanNumberPattern        loan numbers that do not match are marked INVALID instead of being sent
 */
@Validated
@ConfigurationProperties("extractor.publish")
public record PublishProperties(
        @Min(1) @Max(1000) int chunkSize,
        @Min(1) int maxSendAttempts,
        @NotNull Duration retryBackoff,
        @NotNull Duration maxRetryBackoff,
        @NotBlank String eventType,
        @NotBlank String schemaVersion,
        @NotBlank String eventSource,
        @NotBlank String loanNumberPattern) {
}

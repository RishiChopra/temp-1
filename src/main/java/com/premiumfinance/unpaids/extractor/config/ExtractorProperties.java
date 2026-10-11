package com.premiumfinance.unpaids.extractor.config;

import com.premiumfinance.unpaids.extractor.feed.UnpaidFeed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The feeds, and the rules for opening and claiming their daily feeds ({@code extractor.*}).
 * Publishing, Service Bus and housekeeping have their own settings records.
 *
 * @param goLiveDate             nothing dated before this is ever published
 * @param businessZone           time zone for timestamps and logs (business dates come from eodc_date)
 * @param claimExpiresAfter      a claim older than this belongs to an execution that died and can be taken over
 * @param maxAttempts            attempts at a daily feed before it needs attention
 */
@Validated
@ConfigurationProperties("extractor")
public record ExtractorProperties(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate goLiveDate,
        @NotNull ZoneId businessZone,
        @NotNull Duration claimExpiresAfter,
        @Min(1) int maxAttempts,
        @NotEmpty List<@Valid UnpaidFeed> feeds) {
}

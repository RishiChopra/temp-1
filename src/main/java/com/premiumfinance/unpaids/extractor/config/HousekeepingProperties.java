package com.premiumfinance.unpaids.extractor.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Deleting old rows ({@code extractor.housekeeping.*}).
 *
 * @param retentionDays   published ledger entries and finished daily feeds older than this are deleted
 * @param deleteBatchSize rows deleted per table per execution
 */
@Validated
@ConfigurationProperties("extractor.housekeeping")
public record HousekeepingProperties(boolean enabled, @Min(30) int retentionDays, @Min(100) int deleteBatchSize) {
}

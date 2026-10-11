package com.premiumfinance.unpaids.extractor.infrastructure.servicebus;

/**
 * The UnpaidRecorded event, contract v1.0, sent as the JSON body of the Service Bus message.
 * Dates are ISO-8601; occurredAt is UTC.
 *
 * <p>Consumers (e.g. the case creator) depend on these field names: change them only with a new schema
 * version. {@code sourceSystem} carries the loan book (PL or CL).
 */
public record UnpaidRecordedEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String source,
        String occurredAt,
        Data data) {

    public record Data(
            String sourceSystem,
            String origin,
            String loanNumber,
            String unpaidDate,
            String businessDate,
            String sourceTable,
            String sourceReference) {
    }
}

package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.config.PublishProperties;
import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/** Checks an unpaid before it is sent. Entries that fail are marked INVALID with the reason. */
@Component
public class EventValidator {

    private final Pattern loanNumberFormat;

    public EventValidator(PublishProperties properties) {
        this.loanNumberFormat = Pattern.compile(properties.loanNumberPattern());
    }

    /** @return why the entry cannot be published, or empty if it can */
    public Optional<String> problemWith(LedgerEntry entry) {
        if (entry.loanNumber() == null || entry.loanNumber().isBlank()) {
            return Optional.of("LOAN_NUMBER_MISSING");
        }
        if (!loanNumberFormat.matcher(entry.loanNumber()).matches()) {
            return Optional.of("LOAN_NUMBER_FORMAT: '%s' does not match %s"
                    .formatted(entry.loanNumber(), loanNumberFormat.pattern()));
        }
        if (entry.unpaidDate() == null) {
            return Optional.of("UNPAID_DATE_MISSING");
        }
        return Optional.empty();
    }
}

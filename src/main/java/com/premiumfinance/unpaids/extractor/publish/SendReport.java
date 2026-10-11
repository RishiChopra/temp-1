package com.premiumfinance.unpaids.extractor.publish;

import com.premiumfinance.unpaids.extractor.ledger.LedgerEntry;

import java.util.List;

/**
 * What happened to the entries given to {@link UnpaidEventSender#send}.
 *
 * @param sent        accepted by the broker
 * @param tooLarge    can never be sent: the message is bigger than the broker allows
 * @param notAccepted the batch the broker refused (empty when everything went through)
 * @param failure     why it was refused; null when everything went through
 */
public record SendReport(List<LedgerEntry> sent, List<LedgerEntry> tooLarge, List<LedgerEntry> notAccepted,
                         RuntimeException failure) {

    public static final SendReport NOTHING_TO_SEND = new SendReport(List.of(), List.of(), List.of(), null);

    public SendReport {
        sent = List.copyOf(sent);
        tooLarge = List.copyOf(tooLarge);
        notAccepted = List.copyOf(notAccepted);
    }

    public static SendReport accepted(List<LedgerEntry> sent, List<LedgerEntry> tooLarge) {
        return new SendReport(sent, tooLarge, List.of(), null);
    }

    public static SendReport refused(List<LedgerEntry> sent, List<LedgerEntry> tooLarge,
                                     List<LedgerEntry> notAccepted, RuntimeException failure) {
        return new SendReport(sent, tooLarge, notAccepted, failure);
    }

    public boolean hasFailure() {
        return failure != null;
    }
}

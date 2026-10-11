package com.premiumfinance.unpaids.extractor.publish;

/**
 * What publishing a feed's pending entries did.
 *
 * @param sent       entries accepted by the broker
 * @param invalid    entries that can never be sent (bad data, or too large)
 * @param sendFailed the broker refused a batch, so publishing stopped early; those entries retry later
 */
public record PublishResult(int sent, int invalid, boolean sendFailed) {

    public static final PublishResult NONE = new PublishResult(0, 0, false);

    public PublishResult plus(PublishResult other) {
        return new PublishResult(sent + other.sent, invalid + other.invalid, sendFailed || other.sendFailed);
    }
}

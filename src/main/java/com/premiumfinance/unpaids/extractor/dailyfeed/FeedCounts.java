package com.premiumfinance.unpaids.extractor.dailyfeed;

/** What one attempt at a daily feed did. Added up on UNPAID_DAILY_FEED across attempts. */
public record FeedCounts(int staged, int collisions, int sent, int invalid) {

    public static final FeedCounts NONE = new FeedCounts(0, 0, 0, 0);

    public static FeedCounts staged(int staged, int collisions) {
        return new FeedCounts(staged, collisions, 0, 0);
    }

    public FeedCounts withPublished(int sent, int invalid) {
        return new FeedCounts(staged, collisions, sent, invalid);
    }

    public FeedCounts plus(FeedCounts other) {
        return new FeedCounts(staged + other.staged, collisions + other.collisions,
                sent + other.sent, invalid + other.invalid);
    }
}

package com.premiumfinance.unpaids.extractor.ledger;

/**
 * What staging a feed added to the ledger.
 *
 * @param staged     unpaids added, ready to publish
 * @param collisions unpaids recorded as COLLISION because their loan and unpaid date were already taken
 */
public record Staged(int staged, int collisions) {
}

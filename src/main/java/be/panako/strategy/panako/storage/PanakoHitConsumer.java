package be.panako.strategy.panako.storage;

/**
 * Receives database hits one at a time while a query queue is processed, so a caller can
 * aggregate them into whatever compact structure it needs instead of materialising a
 * {@link PanakoHit} object per hit. Panako lookups against a large index produce millions of
 * hits per query, which made the object-per-hit accumulator the dominant heap cost.
 */
@FunctionalInterface
public interface PanakoHitConsumer {
	/**
	 * @param originalHash The fingerprint hash that was used in the query
	 * @param matchedNearHash The (near) fingerprint hash found in the reference database
	 * @param t The time index in the reference, in blocks
	 * @param resourceID The reference resource identifier
	 * @param f The frequency bin of the reference print
	 */
	void accept(long originalHash, long matchedNearHash, int t, int resourceID, int f);
}

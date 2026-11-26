package be.panako.strategy.panako;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.panako.strategy.QueryResult;
import be.panako.strategy.QueryResultHandler;
import be.panako.tests.TestData;
import be.panako.util.Config;
import be.panako.util.FileUtils;
import be.panako.util.Key;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The streaming {@code lookupFingerprints} must report the same matches as the original
 * object-per-hit implementation, including under concurrent lookups (the per-thread query
 * queue is shared storage state).
 */
class StreamingLookupEquivalenceTest {

  private static PanakoStrategy strategy;
  private static final List<File> queries = new ArrayList<>();

  @BeforeAll
  static void indexReferences() {
    // Same LMDB folder as PanakoStrategyTest: the storage is a JVM-wide singleton.
    // LMDB maxreaders comes from AVAILABLE_PROCESSORS (default 1); EarWorm's matcher sets 128.
    Config.set(Key.AVAILABLE_PROCESSORS, "64");
    Config.set(Key.PANAKO_LMDB_FOLDER, FileUtils.combine(FileUtils.temporaryDirectory(), "panako_test_data"));
    strategy = new PanakoStrategy();
    for (File ref : TestData.referenceFiles()) {
      if (!strategy.hasResource(ref.getAbsolutePath())) {
        strategy.store(ref.getAbsolutePath(), ref.getName());
      }
    }
    queries.addAll(TestData.queryFiles());
    queries.addAll(TestData.overTheAirQueryFiles());
  }

  private static List<PanakoFingerprint> printsOf(File query) {
    return PanakoFingerprintCodec.decode(strategy.extract(query.getAbsolutePath()));
  }

  /** A comparable rendering of every reported field, in reported order. */
  private static List<String> run(List<PanakoFingerprint> prints, boolean legacy) {
    List<String> out = Collections.synchronizedList(new ArrayList<>());
    QueryResultHandler handler = new QueryResultHandler() {
      @Override public void handleQueryResult(QueryResult r) {
        out.add(String.format("id=%s q=%.4f-%.4f ref=%.4f-%.4f score=%.0f tf=%.6f ff=%.6f pct=%.6f",
            r.refIdentifier, r.queryStart, r.queryStop, r.refStart, r.refStop, r.score,
            r.timeFactor, r.frequencyFactor, r.percentOfSecondsWithMatches));
      }
      @Override public void handleEmptyResult(QueryResult r) {
        out.add("empty");
      }
    };
    if (legacy) {
      strategy.lookupFingerprintsLegacy("q", prints, 10, new HashSet<>(), handler);
    } else {
      strategy.lookupFingerprints("q", prints, 10, new HashSet<>(), handler);
    }
    return out;
  }

  @Test
  void streamingLookupMatchesLegacyLookup() {
    int matched = 0;
    for (File query : queries) {
      List<PanakoFingerprint> prints = printsOf(query);
      List<String> expected = run(prints, true);
      assertEquals(expected, run(prints, false), "results differ for " + query.getName());
      if (!expected.contains("empty")) matched++;
    }
    // Guard against a vacuous pass: the dataset has queries for indexed references.
    assertTrue(matched >= 10, "expected most known queries to match, matched " + matched);
  }

  @Test
  void concurrentStreamingLookupsMatchSerialResults() throws Exception {
    List<List<PanakoFingerprint>> prints = new ArrayList<>();
    List<List<String>> expected = new ArrayList<>();
    for (File query : queries) {
      List<PanakoFingerprint> p = printsOf(query);
      prints.add(p);
      expected.add(run(p, false));
    }
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      for (int round = 0; round < 3; round++) {
        List<Future<List<String>>> futures = new ArrayList<>();
        for (List<PanakoFingerprint> p : prints) {
          futures.add(pool.submit(() -> run(p, false)));
        }
        for (int i = 0; i < futures.size(); i++) {
          assertEquals(expected.get(i), futures.get(i).get(), "concurrent result differs for " + queries.get(i).getName());
        }
      }
    } finally {
      pool.shutdownNow();
    }
    assertFalse(expected.isEmpty());
  }
}

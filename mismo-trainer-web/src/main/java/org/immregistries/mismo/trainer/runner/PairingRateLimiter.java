package org.immregistries.mismo.trainer.runner;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Limits failed pairing attempts per client address: at most {@link #MAX_FAILURES} in
 * {@link #WINDOW_MILLIS} (docs/island-runner-implementation-plan.md §2). The pairing route is the
 * only unauthenticated one. Kept in memory: a restart clears it, which is acceptable given the
 * code space (~8 x 10^11) and the 15-minute code lifetime.
 */
public final class PairingRateLimiter {

  public static final int MAX_FAILURES = 10;
  public static final long WINDOW_MILLIS = 15L * 60L * 1000L;
  private static final int MAX_TRACKED_ADDRESSES = 10_000;

  private final Map<String, Deque<Long>> failures = new HashMap<String, Deque<Long>>();
  private final LongSupplier clock;

  public PairingRateLimiter() {
    this(System::currentTimeMillis);
  }

  /** For tests. */
  PairingRateLimiter(LongSupplier clock) {
    this.clock = clock;
  }

  /** @return {@code true} if {@code address} has used up its failed attempts for now */
  public synchronized boolean isBlocked(String address) {
    Deque<Long> times = failures.get(address);
    if (times == null) {
      return false;
    }
    prune(times, clock.getAsLong());
    if (times.isEmpty()) {
      failures.remove(address);
      return false;
    }
    return times.size() >= MAX_FAILURES;
  }

  public synchronized void recordFailure(String address) {
    long now = clock.getAsLong();
    if (failures.size() >= MAX_TRACKED_ADDRESSES) {
      pruneAll(now);
    }
    failures.computeIfAbsent(address, key -> new ArrayDeque<Long>()).addLast(now);
  }

  private static void prune(Deque<Long> times, long now) {
    while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MILLIS) {
      times.removeFirst();
    }
  }

  private void pruneAll(long now) {
    for (Iterator<Deque<Long>> it = failures.values().iterator(); it.hasNext();) {
      Deque<Long> times = it.next();
      prune(times, now);
      if (times.isEmpty()) {
        it.remove();
      }
    }
  }
}

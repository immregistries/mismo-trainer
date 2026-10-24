package org.immregistries.mismo.trainer.runner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class PairingRateLimiterTest {

  @Test
  public void blocksAfterTenFailuresUntilTheWindowPasses() {
    AtomicLong now = new AtomicLong(1_000_000L);
    PairingRateLimiter limiter = new PairingRateLimiter(now::get);
    for (int i = 0; i < PairingRateLimiter.MAX_FAILURES - 1; i++) {
      limiter.recordFailure("10.0.0.1");
      now.addAndGet(1000);
    }
    assertFalse(limiter.isBlocked("10.0.0.1"));
    limiter.recordFailure("10.0.0.1");
    assertTrue(limiter.isBlocked("10.0.0.1"));
    assertFalse("other addresses are unaffected", limiter.isBlocked("10.0.0.2"));

    // The oldest failure ages out of the window first.
    now.set(1_000_000L + PairingRateLimiter.WINDOW_MILLIS);
    assertFalse(limiter.isBlocked("10.0.0.1"));
  }
}

package org.immregistries.mismo.runner;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RunnerVersionTest {
  @Test
  public void clientVersionIdentifiesCorrectedCopyBehavior() {
    assertTrue(RunnerMain.VERSION.endsWith("+copy1"));
  }
}

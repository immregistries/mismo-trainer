package org.immregistries.mismo.runner;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The runner's private data directory: {@code %LOCALAPPDATA%\Mismo Runner} on Windows,
 * {@code ~/.mismo-runner} elsewhere. {@code MISMO_RUNNER_HOME} overrides it (useful for running
 * two runners on one machine while testing).
 */
final class RunnerHome {

  private RunnerHome() {
  }

  static Path directory() {
    String override = System.getenv("MISMO_RUNNER_HOME");
    if (override != null && !override.isBlank()) {
      return Paths.get(override);
    }
    String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      return Paths.get(localAppData, "Mismo Runner");
    }
    return Paths.get(System.getProperty("user.home"), ".mismo-runner");
  }
}

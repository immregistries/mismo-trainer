package org.immregistries.mismo.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.immregistries.mismo.optimizer.protocol.Protocol;

/**
 * {@code checkpoints\run-<id>.json}: the best candidate of a running run, rewritten whenever it
 * changes and deleted when the run ends cleanly. If the runner dies, the next launch finds the
 * file and offers to upload that candidate and mark the run FAILED
 * (docs/island-runner-implementation-plan.md §5). The population itself is not saved.
 */
final class Checkpoints {

  record Checkpoint(String serverUrl, int runnerId, int runId, int assignmentId, int revision, String assignmentName,
      String seedSha256, String testSetSha256, int bestGeneration, double bestScore, String bestScript,
      long savedAtMillis) {
  }

  private final Path directory;

  Checkpoints(Path runnerHome) {
    this.directory = runnerHome.resolve("checkpoints");
  }

  void save(Checkpoint checkpoint) throws IOException {
    Files.createDirectories(directory);
    Path file = file(checkpoint.runId());
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temp, Protocol.gson().toJson(checkpoint), StandardCharsets.UTF_8);
    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  void delete(int runId) throws IOException {
    Files.deleteIfExists(file(runId));
  }

  /** Checkpoints left by runs of this runner on this server; unreadable files are skipped. */
  List<Checkpoint> list(String serverUrl, int runnerId) throws IOException {
    List<Checkpoint> found = new ArrayList<Checkpoint>();
    if (!Files.isDirectory(directory)) {
      return found;
    }
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "run-*.json")) {
      for (Path file : files) {
        try {
          Checkpoint checkpoint = Protocol.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8),
              Checkpoint.class);
          if (checkpoint != null && checkpoint.runnerId() == runnerId && serverUrl.equals(checkpoint.serverUrl())) {
            found.add(checkpoint);
          }
        } catch (RuntimeException e) {
          RunnerLog.LOG.warning("Skipping unreadable checkpoint " + file + ": " + e);
        }
      }
    }
    return found;
  }

  private Path file(int runId) {
    return directory.resolve("run-" + runId + ".json");
  }
}

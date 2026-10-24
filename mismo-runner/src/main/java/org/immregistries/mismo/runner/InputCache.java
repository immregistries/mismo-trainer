package org.immregistries.mismo.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.immregistries.mismo.optimizer.protocol.Hashes;

/**
 * Run inputs (seed configuration, Test Set snapshot) cached by content hash under
 * {@code cache/<sha256>.<ext>}. Content is checked against its hash when downloaded and again when
 * read back, so a run only ever starts from exactly what the assignment named.
 */
final class InputCache {

  /** Downloads content when it isn't cached. */
  interface Download {
    byte[] fetch() throws ApiClient.ApiError;
  }

  /** The downloaded or cached bytes did not have the expected hash. */
  static final class HashMismatch extends Exception {
    private static final long serialVersionUID = 1L;

    HashMismatch(String message) {
      super(message);
    }
  }

  private final Path directory;

  InputCache(Path runnerHome) {
    this.directory = runnerHome.resolve("cache");
  }

  /** @param what for messages, e.g. "seed configuration" */
  byte[] get(String sha256, String extension, String what, Download download)
      throws IOException, ApiClient.ApiError, HashMismatch {
    Path file = directory.resolve(sha256 + "." + extension);
    if (Files.exists(file)) {
      byte[] cached = Files.readAllBytes(file);
      if (Hashes.sha256Hex(cached).equals(sha256)) {
        return cached;
      }
      Files.delete(file); // corrupted; download it again
    }
    byte[] content = download.fetch();
    String actual = Hashes.sha256Hex(content);
    if (!actual.equals(sha256)) {
      throw new HashMismatch("The downloaded " + what + " does not match the assignment (expected SHA-256 "
          + sha256.substring(0, 12) + "..., got " + actual.substring(0, 12) + "...).");
    }
    Files.createDirectories(directory);
    Path temp = directory.resolve(sha256 + "." + extension + ".tmp");
    Files.write(temp, content);
    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    return content;
  }
}

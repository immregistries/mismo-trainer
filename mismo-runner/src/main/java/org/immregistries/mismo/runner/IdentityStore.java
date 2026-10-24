package org.immregistries.mismo.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.immregistries.mismo.optimizer.protocol.Protocol;

/**
 * Reads and writes {@code identity.json}: the server this installation is paired with and its
 * credential. The file sits in the user's own profile directory, which Windows already restricts
 * to that user (docs/island-runner-implementation-plan.md §5). The credential is never printed.
 */
final class IdentityStore {

  /** What pairing produced. {@code credential} is a secret. */
  record Identity(String serverUrl, int runnerId, String runnerName, String organizationName, String credential) {
  }

  private final Path file;

  IdentityStore(Path directory) {
    this.file = directory.resolve("identity.json");
  }

  /** @return the saved identity, or {@code null} if this installation has not been paired */
  Identity load() throws IOException {
    if (!Files.exists(file)) {
      return null;
    }
    Identity identity = Protocol.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Identity.class);
    if (identity == null || identity.serverUrl() == null || identity.credential() == null) {
      return null;
    }
    return identity;
  }

  /** Writes to a temporary file first so a crash can't leave a half-written identity. */
  void save(Identity identity) throws IOException {
    Files.createDirectories(file.getParent());
    Path temp = file.resolveSibling("identity.json.tmp");
    Files.writeString(temp, Protocol.gson().toJson(identity), StandardCharsets.UTF_8);
    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  Path path() {
    return file;
  }
}

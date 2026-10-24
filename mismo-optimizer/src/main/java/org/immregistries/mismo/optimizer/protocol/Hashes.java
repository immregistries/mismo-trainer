package org.immregistries.mismo.optimizer.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helpers. The hashing rule for run inputs is SHA-256 over the exact UTF-8 bytes served
 * (docs/island-runner-implementation-plan.md D3), so the runner hashes a response body before
 * parsing it.
 */
public final class Hashes {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private Hashes() {
  }

  /** @return lowercase hex SHA-256 of {@code bytes}, the same form MySQL's {@code SHA2(x, 256)} returns */
  public static String sha256Hex(byte[] bytes) {
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
    char[] out = new char[digest.length * 2];
    for (int i = 0; i < digest.length; i++) {
      out[i * 2] = HEX[(digest[i] >> 4) & 0xF];
      out[i * 2 + 1] = HEX[digest[i] & 0xF];
    }
    return new String(out);
  }

  /** @return lowercase hex SHA-256 of {@code text}'s UTF-8 bytes */
  public static String sha256Hex(String text) {
    return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
  }
}

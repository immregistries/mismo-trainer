package org.immregistries.mismo.trainer.runner;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import org.immregistries.mismo.optimizer.protocol.Hashes;

/**
 * Pairing codes and durable credentials for Runners (docs/island-runner-implementation-plan.md
 * §2). Only {@link #hash} of either is ever stored; the raw values are shown or returned once.
 */
public final class RunnerCredentialSupport {

  /** 31 characters with no I, L, O, 0 or 1, so a code read aloud or copied by hand can't be misread. */
  private static final String PAIRING_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final String CREDENTIAL_PREFIX = "mrn_";
  public static final long PAIRING_VALID_MILLIS = 15L * 60L * 1000L;

  private static final SecureRandom RANDOM = new SecureRandom();

  private RunnerCredentialSupport() {
  }

  /** @return a new pairing code in the form {@code XXXX-XXXX} */
  public static String generatePairingCode() {
    StringBuilder sb = new StringBuilder(9);
    for (int i = 0; i < 8; i++) {
      if (i == 4) {
        sb.append('-');
      }
      sb.append(PAIRING_ALPHABET.charAt(RANDOM.nextInt(PAIRING_ALPHABET.length())));
    }
    return sb.toString();
  }

  /**
   * Puts a typed pairing code into the canonical {@code XXXX-XXXX} form (upper case, spaces and
   * dashes ignored), or returns {@code null} if it can't be one.
   */
  public static String normalizePairingCode(String typed) {
    if (typed == null) {
      return null;
    }
    String compact = typed.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    if (compact.length() != 8) {
      return null;
    }
    for (int i = 0; i < compact.length(); i++) {
      if (PAIRING_ALPHABET.indexOf(compact.charAt(i)) < 0) {
        return null;
      }
    }
    return compact.substring(0, 4) + "-" + compact.substring(4);
  }

  /** @return a new durable credential: {@code mrn_} plus 32 random bytes, base64url */
  public static String generateCredential() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return CREDENTIAL_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** @return the value stored in {@code runner.pairing_code_hash} / {@code credential_hash} */
  public static String hash(String rawValue) {
    return Hashes.sha256Hex(rawValue);
  }
}

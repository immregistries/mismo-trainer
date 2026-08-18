package org.immregistries.mismo.trainer.servlet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.IslandCredential;

/**
 * Machine-credential support for Island optimization processes
 * (database-schema-migration-plan.md §2.6, §3.6). The raw token is generated once, shown to the
 * user exactly at creation time (never stored, never logged), and only its SHA-256 hash is
 * persisted in {@code credential_hash}. Resolving a raw token presented by an Island process is
 * independent of any logged-in user's organization -- unlike {@link OrgScope}, which enforces
 * the boundary for a session {@code User}, this resolves the organization *from* the credential
 * itself, per §3.6's "Mismo-Trainer resolves the credential; determines the organization" flow.
 */
public final class IslandCredentialSupport {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String TOKEN_PREFIX = "isl_";

  private IslandCredentialSupport() {
  }

  /** Generates a new, cryptographically random raw credential token. */
  public static String generateToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Hashes a raw token for storage/lookup in {@code credential_hash}. Never reversible. */
  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hashBytes.length * 2);
      for (byte b : hashBytes) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * Resolves a raw token presented by an Island process to its {@code IslandCredential}, or
   * {@code null} if the token is missing, unknown, or revoked. Callers must reject the request
   * (never fall back to an unscoped/default organization) when this returns {@code null}.
   */
  @SuppressWarnings("unchecked")
  public static IslandCredential resolve(Session dataSession, String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return null;
    }
    Query query = dataSession.createQuery("from IslandCredential where credentialHash = ?");
    query.setParameter(0, hash(rawToken));
    List<IslandCredential> matches = query.list();
    if (matches.isEmpty()) {
      return null;
    }
    IslandCredential credential = matches.get(0);
    if (credential.getRevokedAt() != null || credential.getOrganization() == null) {
      return null;
    }
    return credential;
  }

  /** Records that a credential was just used to authenticate a request. */
  public static void touchLastUsed(Session dataSession, IslandCredential credential) {
    Transaction transaction = dataSession.beginTransaction();
    credential.setLastUsedAt(new Date());
    dataSession.update(credential);
    transaction.commit();
  }
}

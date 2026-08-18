package org.immregistries.mismo.trainer.servlet;

import java.util.Date;
import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.IslandCredential;
import org.immregistries.mismo.trainer.model.MatchItem;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.User;

/**
 * Enforces database-schema-migration-plan.md §4 (Tenant Enforcement Rules): every read or write
 * of {@code match_set}, {@code match_item}, {@code configuration}, or {@code island_credential}
 * is filtered by the authenticated session's organization, never by a client-supplied id alone.
 * {@code match_item} is scoped through its parent {@code match_set}. The bundled MIIS- / AIRA-
 * .txt flat-file corpora are outside this class entirely (section 2.9) -- they stay
 * global/unscoped. Resolving an Island machine credential by its raw token (not by a logged-in
 * user's organization) is handled separately by {@link IslandCredentialSupport}.
 */
public final class OrgScope {

  private OrgScope() {
  }

  private static boolean sameOrganization(Organization organization, User user) {
    return organization != null && user != null && user.getOrganization() != null
        && organization.getOrganizationId() == user.getOrganization().getOrganizationId();
  }

  /**
   * Loads a {@code MatchSet} by id, returning {@code null} (never another organization's row)
   * unless it belongs to {@code user}'s organization.
   */
  public static MatchSet loadMatchSet(Session dataSession, int matchSetId, User user) {
    MatchSet matchSet = (MatchSet) dataSession.get(MatchSet.class, matchSetId);
    return sameOrganization(matchSet == null ? null : matchSet.getOrganization(), user) ? matchSet : null;
  }

  /**
   * Loads a {@code MatchItem} by id, verified through its parent {@code MatchSet}'s
   * organization.
   */
  public static MatchItem loadMatchItem(Session dataSession, int matchItemId, User user) {
    MatchItem matchItem = (MatchItem) dataSession.get(MatchItem.class, matchItemId);
    if (matchItem == null
        || !sameOrganization(matchItem.getMatchSet() == null ? null : matchItem.getMatchSet().getOrganization(),
            user)) {
      return null;
    }
    return matchItem;
  }

  /**
   * Loads a {@code Configuration} by id, returning {@code null} unless it belongs to
   * {@code user}'s organization.
   */
  public static Configuration loadConfiguration(Session dataSession, int configurationId, User user) {
    Configuration configuration = (Configuration) dataSession.get(Configuration.class, configurationId);
    return sameOrganization(configuration == null ? null : configuration.getOrganization(), user) ? configuration
        : null;
  }

  @SuppressWarnings("unchecked")
  public static List<MatchSet> listMatchSets(Session dataSession, User user) {
    Query query = dataSession.createQuery("from MatchSet where organization = ? order by updateDate desc");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  @SuppressWarnings("unchecked")
  public static List<Configuration> listConfigurations(Session dataSession, User user) {
    Query query = dataSession.createQuery(
        "from Configuration where organization = ? order by worldName, islandName, generation desc");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /** Creates and saves a new, organization-owned {@code MatchSet}, attributed to {@code user}. */
  public static MatchSet createMatchSet(Session dataSession, String label, User user) {
    Date now = new Date();
    MatchSet matchSet = new MatchSet();
    matchSet.setLabel(label);
    matchSet.setOrganization(user.getOrganization());
    matchSet.setCreatedByUser(user);
    matchSet.setUpdatedByUser(user);
    matchSet.setUpdateDate(now);
    matchSet.setCreatedAt(now);
    matchSet.setUpdatedAt(now);
    dataSession.save(matchSet);
    return matchSet;
  }

  @SuppressWarnings("unchecked")
  public static List<IslandCredential> listIslandCredentials(Session dataSession, User user) {
    Query query = dataSession.createQuery("from IslandCredential where organization = ? order by createdAt desc");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /** Creates and saves a new, organization-owned {@code IslandCredential}, attributed to {@code user}. */
  public static IslandCredential createIslandCredential(Session dataSession, String name, String credentialHash,
      User user) {
    IslandCredential credential = new IslandCredential();
    credential.setName(name);
    credential.setCredentialHash(credentialHash);
    credential.setOrganization(user.getOrganization());
    credential.setCreatedByUser(user);
    credential.setCreatedAt(new Date());
    dataSession.save(credential);
    return credential;
  }

  /**
   * Revokes an {@code IslandCredential} if it belongs to {@code user}'s organization.
   *
   * @return {@code true} if the credential was found (in this organization) and is now revoked
   *         (whether just now or already), {@code false} if it doesn't exist or belongs to
   *         another organization
   */
  public static boolean revokeIslandCredential(Session dataSession, int islandCredentialId, User user) {
    IslandCredential credential = (IslandCredential) dataSession.get(IslandCredential.class, islandCredentialId);
    if (!sameOrganization(credential == null ? null : credential.getOrganization(), user)) {
      return false;
    }
    if (credential.getRevokedAt() == null) {
      credential.setRevokedAt(new Date());
      dataSession.update(credential);
    }
    return true;
  }
}

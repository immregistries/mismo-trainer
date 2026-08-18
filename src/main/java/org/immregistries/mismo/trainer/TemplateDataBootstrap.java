package org.immregistries.mismo.trainer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AnnotationConfiguration;
import org.immregistries.mismo.trainer.model.MatchItem;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.servlet.OrgScope;
import org.immregistries.mismo.trainer.servlet.TestSetUploadServlet;

/**
 * One-time, idempotent bootstrap that loads the two starter "templates" AIRA publishes for every
 * other organization to see and copy (database-schema-migration-plan.md §2.10; v2-roadmap.md
 * §8): {@code legacy-test-data/AIRA-D.txt} into an AIRA-owned, {@code is_template=true}
 * {@code match_set}, and {@code legacy-test-data/Configuration.yml} into an AIRA-owned,
 * {@code is_template=true} {@code configuration}.
 *
 * <p>Check-before-insert, matching {@code LoginServlet}'s org-resolution style: each half looks
 * for its already-created row before creating one, so running this more than once (e.g. after a
 * database restore that re-applies {@code src/db/unapplied_updates.sql}) never duplicates data.
 *
 * <p>Run from the project root, after applying {@code src/db/unapplied_updates.sql}'s Phase 7
 * block (which creates/publishes the AIRA organization as template-eligible) against the target
 * database:
 * <pre>{@code
 * mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * mvn -o compile
 * java -cp "target/classes;target/cp.txt-as-read" org.immregistries.mismo.trainer.TemplateDataBootstrap
 * }</pre>
 * {@code mvn exec:java} does <em>not</em> work for this: {@code pom.xml}'s {@code exec-maven-plugin}
 * configuration hardcodes {@code <mainClass>org.immregistries.mismo.trainer.Island</mainClass>},
 * which silently overrides any {@code -Dexec.mainClass=...} passed on the command line.
 */
public class TemplateDataBootstrap {

  private static final String TEST_SET_FILE = "legacy-test-data/AIRA-D.txt";
  private static final String TEST_SET_LABEL = "AIRA-D";

  private static final String CONFIGURATION_FILE = "legacy-test-data/Configuration.yml";
  private static final String CONFIGURATION_WORLD_NAME = "AIRA Template";
  private static final String CONFIGURATION_ISLAND_NAME = "Configuration.yml";

  public static void main(String[] args) throws Exception {
    SessionFactory factory = new AnnotationConfiguration().configure().buildSessionFactory();
    Session dataSession = factory.openSession();
    try {
      Organization aira = findTemplateOrganization(dataSession);
      if (aira == null) {
        throw new IllegalStateException(
            "No organization.is_template_org = true row found -- apply src/db/unapplied_updates.sql's"
                + " Phase 7 block against this database first (it publishes AIRA, matched by"
                + " domain = 'immregistries.org').");
      }
      OrgScope.requireTemplateEligible(aira);

      Transaction transaction = dataSession.beginTransaction();
      bootstrapMatchSet(dataSession, aira);
      bootstrapConfiguration(dataSession, aira);
      transaction.commit();
    } finally {
      dataSession.close();
      factory.close();
    }
  }

  /**
   * AIRA is a real organization, not a synthetic "system" one (§2.10) -- the same one
   * {@code LoginServlet}'s email-domain auto-provisioning already creates/reuses for
   * {@code *@immregistries.org} logins. Found here by {@code is_template_org = true}, which
   * {@code src/db/unapplied_updates.sql}'s Phase 7 block sets on it by domain.
   */
  private static Organization findTemplateOrganization(Session dataSession) {
    Query query = dataSession.createQuery("from Organization where templateOrg = true");
    @SuppressWarnings("unchecked")
    List<Organization> templateOrgs = query.list();
    return templateOrgs.isEmpty() ? null : templateOrgs.get(0);
  }

  private static void bootstrapMatchSet(Session dataSession, Organization aira) throws IOException {
    Query existing = dataSession.createQuery(
        "from MatchSet where organization = ? and label = ? and template = true");
    existing.setParameter(0, aira);
    existing.setParameter(1, TEST_SET_LABEL);
    @SuppressWarnings("unchecked")
    List<MatchSet> found = existing.list();
    if (!found.isEmpty()) {
      System.out.println("Template match set \"" + TEST_SET_LABEL + "\" already exists (match_set_id="
          + found.get(0).getMatchSetId() + ") -- skipping.");
      return;
    }

    Date now = new Date();
    MatchSet matchSet = new MatchSet();
    matchSet.setLabel(TEST_SET_LABEL);
    matchSet.setOrganization(aira);
    matchSet.setTemplate(true);
    matchSet.setCreatedAt(now);
    matchSet.setUpdatedAt(now);
    dataSession.save(matchSet);

    List<MatchItem> matchItemList;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(TEST_SET_FILE)))) {
      // Reuses TestSetUploadServlet's own TEST:/EXPECT:/PATIENT A:/PATIENT B: parser instead of
      // re-implementing it (v2-roadmap.md §8).
      matchItemList = TestSetUploadServlet.parseMatchItems(in);
    }
    for (MatchItem matchItem : matchItemList) {
      matchItem.setMatchSet(matchSet);
      matchItem.setDataSource(TEST_SET_LABEL);
      matchItem.setCreatedAt(now);
      matchItem.setUpdatedAt(now);
      dataSession.save(matchItem);
    }
    System.out.println("Created template match set \"" + TEST_SET_LABEL + "\" (match_set_id="
        + matchSet.getMatchSetId() + ") with " + matchItemList.size() + " match items.");
  }

  private static void bootstrapConfiguration(Session dataSession, Organization aira) throws IOException {
    Query existing = dataSession.createQuery(
        "from Configuration where organization = ? and worldName = ? and islandName = ? and template = true");
    existing.setParameter(0, aira);
    existing.setParameter(1, CONFIGURATION_WORLD_NAME);
    existing.setParameter(2, CONFIGURATION_ISLAND_NAME);
    @SuppressWarnings("unchecked")
    List<org.immregistries.mismo.trainer.model.Configuration> found = existing.list();
    if (!found.isEmpty()) {
      System.out.println("Template configuration \"" + CONFIGURATION_WORLD_NAME + " / " + CONFIGURATION_ISLAND_NAME
          + "\" already exists (configuration_id=" + found.get(0).getConfigurationId() + ") -- skipping.");
      return;
    }

    // Same Configuration(InputStream) + .setup() canonicalization CentralServlet already
    // performs on Island-submitted scripts (v2-roadmap.md §8) -- here reading the structured
    // Configuration.yml source instead of a raw configurationScript string.
    org.immregistries.mismo.match.model.Configuration matchConfiguration;
    try (FileInputStream in = new FileInputStream(CONFIGURATION_FILE)) {
      matchConfiguration = new org.immregistries.mismo.match.model.Configuration(in);
    }
    matchConfiguration.setup();

    org.immregistries.mismo.trainer.model.Configuration configuration =
        new org.immregistries.mismo.trainer.model.Configuration();
    configuration.setWorldName(CONFIGURATION_WORLD_NAME);
    configuration.setIslandName(CONFIGURATION_ISLAND_NAME);
    configuration.setGeneration(0);
    configuration.setGenerationScore(0.0);
    configuration.setGeneratedDate(new Date());
    configuration.setCreatedAt(new Date());
    configuration.setConfigurationScript(matchConfiguration.getConfigurationScript());
    configuration.setHashForSignature(matchConfiguration.getHashForSignature());
    configuration.setOrganization(aira);
    configuration.setTemplate(true);
    dataSession.save(configuration);
    System.out.println("Created template configuration \"" + CONFIGURATION_WORLD_NAME + " / "
        + CONFIGURATION_ISLAND_NAME + "\" (configuration_id=" + configuration.getConfigurationId() + ").");
  }
}

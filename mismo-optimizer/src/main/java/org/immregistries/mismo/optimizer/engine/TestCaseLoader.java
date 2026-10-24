package org.immregistries.mismo.optimizer.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;

/**
 * Builds the mismo-match runtime {@code MatchItem}s that {@code PatientCompare}/{@link Scorer}
 * score, from either the legacy flat test-case file format or the stored string fields of a
 * Test Set case. Shared by the webapp (from Hibernate rows) and the Island Runner (from Test Set
 * snapshot JSON).
 *
 * @author Nathan Bunker
 *
 */
public final class TestCaseLoader {

  private TestCaseLoader() {
  }

  /**
   * Reads lines a buffered reader and creates a set of MatchTestCases. This may
   * be used to display to the user or to start and optimization run.
   *
   * @param in
   *           open buffered reader
   * @return a list of MatchTestCase ojects
   * @throws IOException
   */
  public static List<MatchItem> readSourceFile(BufferedReader in) throws IOException {
    List<MatchItem> matchItemList = new ArrayList<MatchItem>();
    String label = null;
    String description = null;
    String expectStatus = null;
    String patientDataA = null;
    String patientDataB = null;
    String line = "";
    while ((line = in.readLine()) != null) {
      if (line.startsWith("TEST:")) {
        if (label != null) {
          matchItemList.add(toRuntimeMatchItem(label, description, expectStatus, patientDataA, patientDataB));
        }
        label = readValue(line);
        description = null;
        expectStatus = null;
        patientDataA = null;
        patientDataB = null;
      } else if (label != null) {
        if (line.startsWith("EXPECT:")) {
          expectStatus = readValue(line);
        } else if (line.startsWith("PATIENT A:")) {
          patientDataA = readValue(line);
        } else if (line.startsWith("PATIENT B:")) {
          patientDataB = readValue(line);
        } else if (line.startsWith("DESCRIPTION:")) {
          description = readValue(line);
        }
      }
    }
    if (label != null) {
      matchItemList.add(toRuntimeMatchItem(label, description, expectStatus, patientDataA, patientDataB));
    }
    return matchItemList;
  }

  private static String readValue(String s) {
    int pos = s.indexOf(":");
    if (pos == -1) {
      return "";
    } else {
      return s.substring(pos + 1).trim();
    }
  }

  /**
   * Builds the mismo-match runtime {@code MatchItem} that {@code PatientCompare}/{@code Scorer}
   * need for actual comparison out of a Test Set case's stored strings. Only
   * {@code patientDataA}/{@code patientDataB} are read by {@code PatientCompare.setMatchItem},
   * but the rest are copied too since callers also display label/description/expectStatus.
   *
   * <p><b>Root cause of three Phase 8 NPEs</b> (v2-roadmap.md §10): the runtime
   * {@code org.immregistries.mismo.match.model.MatchItem} class defaults every String field
   * (including {@code description}) to {@code ""} in its own constructor, and every caller of
   * this class -- {@code TestMatchingServlet}, {@code ReviewServlet}, etc. -- relies on that
   * guarantee, calling {@code .getDescription().equals("")} directly rather than null-checking.
   * That guarantee held under the old v1 flow, which read flat files straight into this same
   * class. It broke once persistence moved to a separate, Hibernate-mapped trainer entity
   * (Phase 2): {@code description} is a nullable column, genuinely {@code NULL} for roughly 13%
   * of live rows (all 3,398 items of the AIRA-D template, which have no {@code DESCRIPTION:}
   * line in the source file) -- and the conversion was copying that raw, possibly-null value
   * straight across, silently overwriting the runtime class's own safe {@code ""} default. This
   * fixes it at the one place the invariant is supposed to be established, instead of
   * null-guarding every downstream caller individually. The same defaulting applies to every
   * other String field this method copies, for the same reason.
   *
   * @return an equivalent mismo-match runtime object, safe to hand to PatientCompare/Scorer
   */
  public static MatchItem toRuntimeMatchItem(String label, String description, String expectStatus,
      String patientDataA, String patientDataB) {
    MatchItem runtimeMatchItem = new MatchItem();
    runtimeMatchItem.setLabel(nullToEmpty(label));
    runtimeMatchItem.setDescription(nullToEmpty(description));
    runtimeMatchItem.setPatientDataA(nullToEmpty(patientDataA));
    runtimeMatchItem.setPatientDataB(nullToEmpty(patientDataB));
    runtimeMatchItem.setExpectStatus(nullToEmpty(expectStatus));
    runtimeMatchItem.setPatientA(new Patient(patientDataA));
    runtimeMatchItem.setPatientB(new Patient(patientDataB));
    return runtimeMatchItem;
  }

  /**
   * Converts a Test Set snapshot served by the Runner API into the runtime cases a {@link World}
   * scores.
   *
   * @throws IllegalArgumentException if the snapshot uses a format this version doesn't read
   */
  public static List<MatchItem> fromTestSetContent(Messages.TestSetContent content) {
    if (content.formatVersion() != Protocol.TEST_SET_FORMAT_VERSION) {
      throw new IllegalArgumentException("Unsupported Test Set format version " + content.formatVersion());
    }
    List<MatchItem> matchItemList = new ArrayList<MatchItem>();
    for (Messages.TestCase testCase : content.cases()) {
      matchItemList.add(toRuntimeMatchItem(testCase.label(), testCase.description(), testCase.expectStatus(),
          testCase.patientDataA(), testCase.patientDataB()));
    }
    return matchItemList;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

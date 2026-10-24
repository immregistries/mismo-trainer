package org.immregistries.mismo.optimizer.engine;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.immregistries.mismo.match.model.Configuration;

/**
 * Which patient fields a configuration can use: the fields of its enabled detectors
 * (docs/island-runner-implementation-plan.md D6, item 9). The seed configuration, not the scoring
 * profile, decides this, because the optimizer can disable a detector but never re-enable one
 * (docs/optimization-objectives-and-scoring-profiles.md, "Mechanical note").
 */
public final class FieldScope {

  private static volatile Set<String> measurementCore = null;

  private FieldScope() {
  }

  /** @return the sorted field names {@code configurationScript}'s enabled detectors read */
  public static Set<String> of(String configurationScript) {
    return Collections.unmodifiableSet(new TreeSet<String>(new Configuration(configurationScript).getPatientFieldSet()));
  }

  /**
   * The fields enabled in the shipped {@code mismo-matcher.yml}: the narrow, universal field set
   * of the national measurement instrument (matching-engine.md §10a).
   */
  public static Set<String> measurementCore() {
    Set<String> core = measurementCore;
    if (core == null) {
      Configuration defaults = new Configuration();
      defaults.setup();
      core = Collections.unmodifiableSet(new TreeSet<String>(defaults.getPatientFieldSet()));
      measurementCore = core;
    }
    return core;
  }

  /** @return fields {@code configurationScript} uses that the measurement core doesn't (empty if none) */
  public static Set<String> outsideMeasurementCore(String configurationScript) {
    Set<String> outside = new TreeSet<String>(of(configurationScript));
    outside.removeAll(measurementCore());
    return Collections.unmodifiableSet(outside);
  }
}

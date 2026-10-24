package org.immregistries.mismo.optimizer.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.immregistries.mismo.match.model.Configuration;
import org.junit.Test;

public class FieldScopeTest {

  private static String defaultScript() {
    Configuration configuration = new Configuration();
    configuration.setup();
    return configuration.getConfigurationScript();
  }

  @Test
  public void theShippedConfigurationIsTheMeasurementCore() {
    Set<String> core = FieldScope.measurementCore();
    System.out.println("Measurement core fields: " + core);
    assertTrue(core.contains("nameFirst"));
    assertTrue(core.contains("birthDate"));
    assertEquals(core, FieldScope.of(defaultScript()));
    assertTrue(FieldScope.outsideMeasurementCore(defaultScript()).isEmpty());
  }

  @Test
  public void enablingAnExtraDetectorShowsUpOutsideTheCore() {
    // SH-missing (shot history) ships disabled; turning it on widens the field scope.
    String widened = defaultScript().replace(
        "    SH-missing:\n      label: SH-missing\n      detector: MissingMatchNode\n      fieldName: shotHistory\n      enabled: false\n      minScore: 0.0\n      maxScore: 0.0",
        "    SH-missing:\n      label: SH-missing\n      detector: MissingMatchNode\n      fieldName: shotHistory\n      enabled: true\n      minScore: 0.0\n      maxScore: 0.3");
    assertTrue("test fixture should have changed the script", !widened.equals(defaultScript()));
    assertEquals(Set.of("shotHistory"), FieldScope.outsideMeasurementCore(widened));
  }
}

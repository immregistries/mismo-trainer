package org.immregistries.mismo.trainer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the application version from {@code pom.xml} at build time, rather than a hand-edited
 * constant -- {@code VERSION} used to be a hardcoded literal that silently drifted out of sync
 * with the real project version. Copies InteropHub's own version-resolution pattern
 * ({@code InteropVersionResolver}) directly: a Maven-filtered properties file as the primary
 * source, with the same fallback chain for the (rare) case the app is run without having gone
 * through a Maven build (docs/production-deployment-plan.md).
 */
public class SoftwareVersion {

  private static final String APP_VERSION_PROPERTIES_PATH = "/mismo-version.properties";
  private static final String POM_PROPERTIES_PATH =
      "/META-INF/maven/org.immregistries/mismo-trainer/pom.properties";

  public static final String VERSION = resolveVersion();

  private SoftwareVersion() {
  }

  private static String resolveVersion() {
    String appVersion = readVersionFromProperties(APP_VERSION_PROPERTIES_PATH, "software.version");
    if (appVersion != null && !appVersion.startsWith("${")) {
      return appVersion;
    }

    String pomVersion = readVersionFromProperties(POM_PROPERTIES_PATH, "version");
    if (pomVersion != null) {
      return pomVersion;
    }

    Package pkg = SoftwareVersion.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
      return pkg.getImplementationVersion().trim();
    }

    return "development";
  }

  private static String readVersionFromProperties(String path, String key) {
    Properties properties = new Properties();
    try (InputStream in = SoftwareVersion.class.getResourceAsStream(path)) {
      if (in == null) {
        return null;
      }
      properties.load(in);
      String value = properties.getProperty(key);
      if (value == null || value.isBlank()) {
        return null;
      }
      return value.trim();
    } catch (IOException ex) {
      return null;
    }
  }
}

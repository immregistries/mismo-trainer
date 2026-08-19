package org.immregistries.mismo.trainer;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;

/**
 * Builds the shared Hibernate {@link SessionFactory}, overriding connection credentials from
 * environment variables rather than reading them from {@code hibernate.cfg.xml} -- copied
 * directly from InteropHub's own {@code HibernateUtil}
 * ({@code C:\dev\immregistries\InteropHub\src\main\java\org\airahub\interophub\config\HibernateUtil.java}),
 * renamed to this project's prefix, per explicit direction to match that project's convention
 * (docs/production-deployment-plan.md).
 *
 * <p>{@code hibernate.cfg.xml} keeps a bare {@code driver_class}/{@code connection.url} (with a
 * localhost default -- safe to commit, neither is a secret) but no username or password. Both are
 * required here, sourced only from the environment, and building the session factory fails fast
 * with a clear message if either is missing -- a deployment must never fall back to a hardcoded
 * default credential, and a missing one should fail loudly at startup rather than surface later as
 * a confusing connection error.
 *
 * <p>Every {@code SessionFactory} bootstrap in this project must go through {@link #build()}
 * rather than calling {@code new AnnotationConfiguration().configure().buildSessionFactory()}
 * directly, so the override logic lives in exactly one place.
 */
public final class HibernateSessionFactorySupport {

  private static final String ENV_DB_DRIVER = "MISMO_DB_DRIVER";
  private static final String ENV_DRIVER = "MISMO_DRIVER";
  private static final String ENV_DB_URL = "MISMO_DB_URL";
  private static final String ENV_DB_USER = "MISMO_DB_USER";
  private static final String ENV_USER = "MISMO_USER";
  private static final String ENV_DB_PASSWORD = "MISMO_DB_PASSWORD";
  private static final String ENV_PASSWORD = "MISMO_PASSWORD";

  private static final Logger LOGGER = Logger.getLogger(HibernateSessionFactorySupport.class.getName());

  private HibernateSessionFactorySupport() {
  }

  /** Builds a new {@code SessionFactory} from {@code hibernate.cfg.xml}, with credentials applied. */
  public static SessionFactory build() {
    AnnotationConfiguration configuration = new AnnotationConfiguration().configure();
    applyDatabaseOverrides(configuration);
    return configuration.buildSessionFactory();
  }

  private static void applyDatabaseOverrides(AnnotationConfiguration configuration) {
    configuration.setProperty(
        "hibernate.connection.driver_class",
        getEnvironmentVariableOrDefault(
            ENV_DB_DRIVER,
            ENV_DRIVER,
            configuration.getProperty("hibernate.connection.driver_class")));
    configuration.setProperty(
        "hibernate.connection.url",
        getEnvironmentVariableOrDefault(
            ENV_DB_URL,
            null,
            configuration.getProperty("hibernate.connection.url")));
    configuration.setProperty(
        "hibernate.connection.username",
        requireEnvironmentVariable(ENV_DB_USER, ENV_USER));
    configuration.setProperty(
        "hibernate.connection.password",
        requireEnvironmentVariable(ENV_DB_PASSWORD, ENV_PASSWORD));
  }

  private static String getEnvironmentVariableOrDefault(String envVarName, String aliasEnvVarName,
      String defaultValue) {
    String value = getEnvironmentVariable(envVarName);
    if (value != null) {
      return value;
    }
    if (aliasEnvVarName != null) {
      value = getEnvironmentVariable(aliasEnvVarName);
      if (value != null) {
        LOGGER.log(Level.INFO, "Using environment variable {0} for Hibernate configuration.", aliasEnvVarName);
        return value;
      }
    }
    LOGGER.log(Level.WARNING, "Environment variable {0}{1} is not set. Falling back to configured default.",
        new Object[] {envVarName, aliasEnvVarName == null ? "" : " (or " + aliasEnvVarName + ")"});
    return defaultValue;
  }

  private static String requireEnvironmentVariable(String envVarName, String aliasEnvVarName) {
    String value = getEnvironmentVariable(envVarName);
    if (value != null) {
      return value;
    }
    if (aliasEnvVarName != null) {
      value = getEnvironmentVariable(aliasEnvVarName);
      if (value != null) {
        LOGGER.log(Level.INFO, "Using environment variable {0} for Hibernate configuration.", aliasEnvVarName);
        return value;
      }
    }
    String message = String.format("Required environment variable %s%s is not set.",
        envVarName, aliasEnvVarName == null ? "" : " (or " + aliasEnvVarName + ")");
    LOGGER.severe(message);
    throw new IllegalStateException(message);
  }

  private static String getEnvironmentVariable(String envVarName) {
    String value = System.getenv(envVarName);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}

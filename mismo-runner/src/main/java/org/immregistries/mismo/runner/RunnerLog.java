package org.immregistries.mismo.runner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Detailed log for support, written to {@code logs\runner-YYYYMMDD.log} and kept for 14 days
 * (docs/island-runner-implementation-plan.md §5). The console only ever shows one-line messages;
 * nothing logged here includes the credential.
 */
final class RunnerLog {

  static final Logger LOG = Logger.getLogger("org.immregistries.mismo.runner");
  private static final int KEEP_DAYS = 14;
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
      .withZone(ZoneId.systemDefault());

  private RunnerLog() {
  }

  static void setUp(Path runnerHome) {
    LOG.setUseParentHandlers(false); // keep stack traces off the console
    LOG.setLevel(Level.INFO);
    Path logs = runnerHome.resolve("logs");
    try {
      Files.createDirectories(logs);
      deleteOldLogs(logs);
      String name = "runner-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".log";
      FileHandler handler = new FileHandler(logs.resolve(name).toString(), true);
      handler.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          StringBuilder sb = new StringBuilder();
          sb.append(TIMESTAMP.format(Instant.ofEpochMilli(record.getMillis()))).append(' ')
              .append(record.getLevel()).append(' ').append(formatMessage(record)).append(System.lineSeparator());
          if (record.getThrown() != null) {
            java.io.StringWriter trace = new java.io.StringWriter();
            record.getThrown().printStackTrace(new java.io.PrintWriter(trace));
            sb.append(trace);
          }
          return sb.toString();
        }
      });
      LOG.addHandler(handler);
    } catch (IOException | RuntimeException e) {
      System.out.println("Logging to " + logs + " is unavailable (" + e.getMessage() + ").");
    }
  }

  private static void deleteOldLogs(Path logs) throws IOException {
    Instant cutoff = Instant.now().minus(KEEP_DAYS, ChronoUnit.DAYS);
    try (DirectoryStream<Path> files = Files.newDirectoryStream(logs, "runner-*.log*")) {
      for (Path file : files) {
        if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
          Files.deleteIfExists(file);
        }
      }
    }
  }
}

package org.immregistries.mismo.trainer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.PatientMatcher;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.Scorer;
import org.immregistries.mismo.trainer.model.World;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * This is the command line entry point for running the optimization process.
 * 
 * @author Nathan Bunker
 * 
 */
public class Island {
  /**
   * To run this via the command line, follow these steps:
   * <p>
   * 1. Startup local CentralServlet or obtain URL to CentralServlet you will
   * connect to.
   * <p>
   * 2. Identify source file for you tests. The default tests are currently
   * checked into the project.
   * <p>
   * 3. Modify and use this command line from the root of the project:
   * <p>
   * <code>mvn exec:java -Dexec.mainClass="org.immregistries.mismo.trainer.Island""</code>
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    String centralUrlString;
    String testCasesFilename;
    int worldSize;
    String islandName;
    String worldName;
    {
      String configFileName = "island.yml";
      if (args.length > 0) {
        configFileName = args[0];
      }
      Yaml yaml = new Yaml(new Constructor(Map.class));
      try {
        FileInputStream inputStream = new FileInputStream(configFileName);
        Map<String, Object> data = yaml.load(inputStream);
        centralUrlString = (String) data.get("centralURL");
        testCasesFilename = (String) data.get("testCaseFileName");
        worldName = (String) data.get("worldName");
        islandName = (String) data.get("islandName");
        worldSize = (Integer) data.get("populationSize");
      } catch (FileNotFoundException e) {
        System.err.println("Configuration file not found: " + configFileName);
        return;
      }
    }
    if (worldName == null || worldName.equals("")) {
      System.out.println("Not starting world name.");
      System.exit(0);
    }
    if (islandName == null || islandName.equals("")) {
      System.out.println("Not starting world without island name.");
      System.exit(0);
    }
    System.out.println("Starting patient match optimization island '" + islandName + "' in world '" + worldName
        + "' with " + worldSize + " creatures.");
    System.out.println("Central repository location: " + centralUrlString);

    URL centralUrl = new URL(centralUrlString);
    System.out.println("Asking central server for best configuration to start with.");
    String configurationScript = null;
    try {
      configurationScript = IslandSync.requestStartScript(worldName, islandName, centralUrl);
    } catch (Exception e) {
      System.out.println("Unable to query central server");
      e.printStackTrace(System.err);
    }

    System.out.println("Creating world");
    World world = new World(worldSize, worldName, islandName, configurationScript);

    // Will use score weights from latest configuration that was returned from thes
    // server
    int[][] w = Scorer.getWeights();
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        w[i][j] = world.getCreatures()[0].getPatientCompare().getConfiguration().getScoringWeights()[i][j];
      }
    }

    printWeights(w);

    List<MatchItem> matchItemList;
    {
      System.out.println("Loading test cases from this file: " + testCasesFilename);
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(testCasesFilename)));
      matchItemList = readSourceFile(in);
      System.out.println("  + test case count: " + matchItemList.size());
    }
    world.setMatchItemList(matchItemList);

    System.out.println("Syncing with central server");
    IslandSync islandSync = new IslandSync(world, centralUrl);
    islandSync.start();
    System.out.println("  + Regular island sync started");
    world.start();
    System.out.println("World started on island.");
    while (true) {
      System.out.println();
      System.out.println("  +------------------+------------------+-------+-------+");
      System.out.println("  |      World       |      Island      |  Pop  |  Gen  | ");
      System.out.println("  +------------------+------------------+-------+-------+");
      System.out.println("  | " + pad(world.getWorldName(), 16) + " | " + pad(world.getIslandName(), 16) + " |"
          + pad(worldSize) + " |" + pad(world.getGeneration()) + " | ");
      System.out.println("  +------------------+------------------+-------+-------+");
      System.out.println("   Generating status: " + world.getLastMessage());
      System.out.println("   Score rate (c/s):  " + world.getScoreRate());
      System.out.println("   Sync status:       " + islandSync.getLastMessage());
      Creature[] creatures = world.getCreaturesCopy();
      if (creatures != null) {
        System.out.println();
        System.out.println("  +-------+-------+-------------------------------------------------|");
        System.out.println("  |  Gen  | Score | Signature                                       |");
        System.out.println("  +-------+-------+-------------------------------------------------|");
        for (int i = 0; i < 10 && i < creatures.length; i++) {
          System.out.println("  |" + pad(creatures[i].getGeneration()) + " |"
              + pad((int) (creatures[i].getScore() * 100.0 + 0.5)) + " |"
              + pad(creatures[i].getPatientCompare().getSignature(), 49) + "|");
        }
        System.out.println("  +-------+-------+-------------------------------------------------|");
        if (creatures.length > 0) {
          System.out.println();
          System.out.println("  +--------------+-------+-------+-------+");
          System.out.println("  |              | Match | Poss  | Not M |");
          System.out.println("  +--------------+-------+-------+-------+");
          Scorer scorer = creatures[0].getScorer();
          int[][] c = scorer.getCountTable();
          System.out.println("  | Should Match |" + pad(c[0][0]) + " |" + pad(c[0][1]) + " |" + pad(c[0][2]) + " |");
          System.out.println("  | Should Poss  |" + pad(c[1][0]) + " |" + pad(c[1][1]) + " |" + pad(c[1][2]) + " |");
          System.out.println("  | Should Not M |" + pad(c[2][0]) + " |" + pad(c[2][1]) + " |" + pad(c[2][2]) + " |");
          System.out.println("  +--------------+-------+-------+-------+");
        }
      }
      System.out.println();
      String command = readInput("Command");
      if (command != null && !command.equals("")) {
        if (command.equalsIgnoreCase("exit")) {
          System.out.println("Stopping world");
          world.setKeepRunning(false);
          islandSync.setKeepRunning(false);
          islandSync.interrupt();
          break;
        } else if (command.equalsIgnoreCase("sync")) {
          System.out.println("Syncing now");
          islandSync.interrupt();
        } else if (command.equalsIgnoreCase("weights")) {
          printWeights(w);
        } else if (command.equalsIgnoreCase("script")) {
          if (creatures != null) {
            System.out.println(creatures[0].getPatientCompare().getConfiguration());
          }
        } else if (command.toLowerCase().startsWith("diagnose ")) {
          // diagnose S-4:ADDRESS_CHANGED:IDEAL-GOODA
          String[] parts = command.split(" ");
          if (parts.length <= 1) {
            System.err.println("Usage: diagnose <test>");
          } else {
            String test = parts[1];
            if (creatures != null) {
              boolean found = false;
              for (MatchItem matchItem : world.getMatchItemList()) {

                if (matchItem.getLabel().equals(test)) {
                  PatientCompare patientCompare = new PatientCompare(
                      creatures[0].getPatientCompare().getConfiguration().getConfigurationScript());
                  patientCompare.setMatchItem(matchItem);
                  System.out.println("Result: " + patientCompare.getResult());
                  patientCompare.printOut();
                  found = true;
                  break;
                }
              }
              if (!found) {
                System.err.println("Test not found: " + test);
              }
            }
          }

        } else {
          System.out.println("The following commands are available: ");
          System.out.println(" + exit            - Stop processing and exit");
          System.out.println(" + sync            - Synchronize data to central server");
          System.out.println(" + script          - Print out script for first creature");
          System.out.println(" + weights         - Print out weights being used");
          System.out.println(" + diagnose (test) - Explain decision on single test case");
        }
      }
    }

  }

  private static void printWeights(int[][] w) {
    System.out.println("Using these weights:");
    System.out.println("  +--------------+-------+-------+-------+");
    System.out.println("  | Weights      | Match | Poss  | Not M |");
    System.out.println("  +--------------+-------+-------+-------+");
    System.out.println("  | Should Match |" + pad(w[0][0]) + " |" + pad(w[0][1]) + " |" + pad(w[0][2]) + " |");
    System.out.println("  | Should Poss  |" + pad(w[1][0]) + " |" + pad(w[1][1]) + " |" + pad(w[1][2]) + " |");
    System.out.println("  | Should Not M |" + pad(w[2][0]) + " |" + pad(w[2][1]) + " |" + pad(w[2][2]) + " |");
    System.out.println("  +--------------+-------+-------+-------+");
  }

  private static String readInput(String question) {
    System.out.print(question + ": ");
    // open up standard input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    // read the username from the command-line; need to use try/catch with the
    // readLine() method
    try {
      return br.readLine();
    } catch (IOException ioe) {
      System.out.println("IO error trying to read your name!");
      System.exit(1);
      return null;
    }
  }

  // This is a simplistic way to space out the output
  private static final String PAD = "                                                            ";

  private static String pad(int i) {
    int length = 6;
    return pad(i, length);
  }

  private static String pad(int i, int length) {
    String num = "" + i;
    String padding = PAD + num;
    if (num.length() > length) {
      return num;
    }
    return padding.substring(padding.length() - length);
  }

  private static String pad(String value, int length) {
    if (value.length() > length) {
      return value.substring(0, length);
    }
    String padding = value + PAD;
    return padding.substring(0, length);
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
    MatchItem matchItem = null;
    String line = "";
    while ((line = in.readLine()) != null) {
      if (line.startsWith("TEST:")) {
        if (matchItem != null) {
          matchItemList.add(matchItem);
        }
        matchItem = new MatchItem();
        matchItem.setLabel(readValue(line));
      } else if (matchItem != null) {
        if (line.startsWith("EXPECT:")) {
          matchItem.setExpectStatus(readValue(line));
        } else if (line.startsWith("PATIENT A:")) {
          matchItem.setPatientDataA(readValue(line));
        } else if (line.startsWith("PATIENT B:")) {
          matchItem.setPatientDataB(readValue(line));
        } else if (line.startsWith("DESCRIPTION:")) {
          matchItem.setDescription(readValue(line));
        }
      }
    }
    if (matchItem != null) {
      matchItemList.add(matchItem);
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
}

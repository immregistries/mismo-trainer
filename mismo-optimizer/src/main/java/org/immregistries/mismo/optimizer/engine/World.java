package org.immregistries.mismo.optimizer.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.immregistries.mismo.match.model.Configuration;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;

/**
 * The World is a self-contained sphere of creatures that exist and interact
 * together. This object holds all of their world environment. In addition a
 * specific running instance of a World is called an Island. This is because the
 * creatures from an Island operate in a larger World that they can interact
 * with when the running Islands sync to the central server.
 * 
 * @author Nathan Bunker
 * 
 */
public class World extends Thread
{
  private List<MatchItem> matchItemList;

  /**
   * MatchTestCase list is a set of matches used to test the creatures of this
   * world.
   * 
   * @return list of match test cases
   */
  public List<MatchItem> getMatchItemList() {
    return matchItemList;
  }

  public void setMatchItemList(List<MatchItem> matchTestCaseList) {
    this.matchItemList = matchTestCaseList;
    List<ScoringCase> cases = new ArrayList<ScoringCase>();
    for (MatchItem matchItem : matchTestCaseList) {
      if (matchItem.isExpectedStatusSet()) {
        cases.add(new ScoringCase(matchItem, new Patient(matchItem.getPatientDataA()).prepareForSharing(),
            new Patient(matchItem.getPatientDataB()).prepareForSharing()));
      }
    }
    this.scoringCases = Collections.unmodifiableList(cases);
  }

  /**
   * A scoreable test case with its two patients parsed once, up front. Every creature compares the
   * same {@code Patient} objects, read-only, so the 2 x cases parses happen once per world rather
   * than once per creature per generation.
   */
  record ScoringCase(MatchItem matchItem, Patient patientA, Patient patientB) {
  }

  private List<ScoringCase> scoringCases = Collections.emptyList();

  /** The scoreable cases of {@link #getMatchItemList()}, prepared for sharing across threads. */
  List<ScoringCase> getScoringCases() {
    return scoringCases;
  }

  private int scoringThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  private ExecutorService scoringPool = null;

  /**
   * How many creatures are scored at once. Defaults to one less than the number of processors, so
   * the console stays responsive. Scores don't depend on this; only speed does.
   */
  public int getScoringThreads() {
    return scoringThreads;
  }

  public synchronized void setScoringThreads(int scoringThreads) {
    if (scoringThreads < 1) {
      throw new IllegalArgumentException("At least one scoring thread is needed");
    }
    if (scoringThreads != this.scoringThreads) {
      shutdownScoringPool();
      this.scoringThreads = scoringThreads;
    }
  }

  private ExecutorService scoringPool() {
    if (scoringPool == null) {
      scoringPool = Executors.newFixedThreadPool(scoringThreads, runnable -> {
        Thread thread = new Thread(runnable, "mismo-scoring");
        thread.setDaemon(true);
        return thread;
      });
    }
    return scoringPool;
  }

  /** Stops the scoring threads; a later {@link #scoreAndSort()} starts new ones. */
  public synchronized void shutdownScoringPool() {
    if (scoringPool != null) {
      scoringPool.shutdownNow();
      scoringPool = null;
    }
  }

  /**
   * Indicates the current generation this world is operating in. Every time the
   * creatures are mated and new children are created the generation count is
   * incremented.
   * 
   * @return
   */
  public int getGeneration() {
    return generation;
  }

  public void setGeneration(int generation) {
    this.generation = generation;
  }

  private int lowerCut;
  private int lowerCutStart;
  private double upperCut;
  private Creature[] creatures;
  private Creature[] creaturesCopy;
  private int generation = 0;
  private String worldName = "";
  private String islandName = "";
  private Creature seed = null;
  private boolean rescore = false;
  private int[][] scoringWeights;

  /**
   * The weights every creature in this world is scored with (see {@link Scorer}).
   *
   * @return 3 x 3 scoring weights
   */
  public int[][] getScoringWeights() {
    return scoringWeights;
  }

  public boolean isRescore() {
    return rescore;
  }

  public void setRescore(boolean rescore) {
    this.rescore = rescore;
  }

  /**
   * @param size
   *          the number of creatures that can live in this world
   * @param worldName
   *          the name of this world for display purposes
   * @param islandName
   *          the name of the island within the world for display purposes
   */
  public World(int size, String worldName, String islandName) {
    this(size, worldName, islandName, null);
  }

  /**
   * @param size
   *          the number of creatures that can live in this world
   * @param worldName
   *          the name of this world for display purposes
   * @param islandName
   *          the name of the island within the world for display purposes
   * @param configurationScript
   *          the initial script that all creatures should be started at
   */
  public World(int size, String worldName, String islandName, String configurationScript) {
    this(size, worldName, islandName, configurationScript, null);
  }

  /**
   * @param size
   *          the number of creatures that can live in this world
   * @param worldName
   *          the name of this world for display purposes
   * @param islandName
   *          the name of the island within the world for display purposes
   * @param configurationScript
   *          the initial script that all creatures should be started at
   * @param scoringWeights
   *          3 x 3 weights to score creatures with, or <code>null</code> to use the weights
   *          embedded in the configuration script (the defaults if there is no script)
   */
  public World(int size, String worldName, String islandName, String configurationScript,
      int[][] scoringWeights) {
    {
      Configuration configuration = new Configuration();
      configuration.setConfigurationScript(configurationScript);
      configuration.setup();
      int[][] weights = scoringWeights == null ? configuration.getScoringWeights() : scoringWeights;
      new Scorer(weights); // validates the 3 x 3 shape up front
      this.scoringWeights = new int[3][];
      for (int i = 0; i < 3; i++) {
        this.scoringWeights[i] = weights[i].clone();
      }
      configuration.setWorldName(worldName);
      configuration.setIslandName(islandName);
      configuration.setGeneration(0);
      configuration.setGeneratedDate(new Date());
      configuration.createConfigurationScript();
      configurationScript = configuration.getConfigurationScript(); 
    }

    this.worldName = worldName;
    this.islandName = islandName;
    this.lowerCut = (int) Math.sqrt(0.5 * size);
    lowerCutStart = size - (lowerCut * lowerCut);
    upperCut = (size - lowerCut) * 0.3;
    creatures = new Creature[size];
    for (int i = 0; i < creatures.length; i++) {
      if (configurationScript == null || configurationScript.length() == 0) {
        creatures[i] = new Creature(generation, this);
      } else {
        creatures[i] = new Creature(this, configurationScript);
        creatures[i].setGeneration(0);
      }
      if (i > 0) {
        if (i % 2 == 0) {
          creatures[i].tweak();
        } else {
          creatures[i].randomize();
        }
      }
    }
  }

  /**
   * 
   * @return displayable name of island
   */
  public String getIslandName() {
    return islandName;
  }

  /**
   * @param islandName
   *          identifies this specific running instance from all other instances
   *          that may be running with the defined world
   */
  public void setIslandName(String islandName) {
    this.islandName = islandName;
  }

  /**
   * The world name is an important identity. It is used to determine from which
   * world seed cases should be pulled from and which world to sync back to on
   * the central server.
   * 
   * @return identifies the name of the world this instance is a part of
   */
  public String getWorldName() {
    return worldName;
  }

  /**
   * This method allows a single creature, from a different island, to be
   * "planted" on this running instance. When the world goes through the score
   * and sort phase it will throw this seed into the last position of this
   * world. This is one mechanism for a creature to island-hop.
   * 
   * @param seed
   */
  public void plantSeed(Creature seed) {
    this.seed = seed;
  }

  /**
   * This is a critical step in the optimization algorithm. All of the creatures
   * are examined, and those that have not been scored are scored. Then they are
   * sorted from highest scores to lowest. Finally a copy of this creature list
   * is created so it can be examined while the algorithm continues to progress.
   */
  public synchronized void scoreAndSort() {
    if (seed != null) {
      creatures[creatures.length - 1] = seed;
      seed = null;
    }
    lastMessage = "Scoring Creatures";

    {
      long startTime = System.currentTimeMillis();
      // Each creature's score depends only on its own configuration and the shared, read-only
      // cases, so creatures are scored in parallel; the results are identical to scoring them
      // one at a time.
      List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
      for (Creature creature : creatures) {
        if (!creature.isScored()) {
          tasks.add(() -> {
            if (keepRunning) {
              creature.score();
            }
            return null;
          });
        }
      }
      int scoreCount = tasks.size();
      lastMessage = "Scoring " + scoreCount + " creatures on " + scoringThreads + " threads";
      try {
        for (Future<Void> future : scoringPool().invokeAll(tasks)) {
          future.get();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException e) {
        throw new IllegalStateException("Scoring a creature failed: " + e.getCause(), e.getCause());
      }
      if (!keepRunning) {
        return;
      }
      long endTime = System.currentTimeMillis();
      long elapsedMillis = endTime - startTime;
      if (scoreCount == 0 || elapsedMillis <= 0) {
        scoreRate = 0;
      } else {
        scoreRate = scoreCount * 1000.0 / elapsedMillis;
      }
    }
    if (!keepRunning) {
      return;
    }
    lastMessage = "Sorting Creatures";
    Arrays.sort(creatures, new Comparator<Creature>() {
      public int compare(Creature c1, Creature c2) {
        return c1.getScore() > c2.getScore() ? -1 : (c1.getScore() < c2.getScore() ? 1 : 0);
      }
    });
    if (!keepRunning) {
      return;
    }
    lastMessage = "Copy Creatures";
    creaturesCopy = new Creature[creatures.length];
    for (int i = 0; i < creatures.length; i++) {
      creaturesCopy[i] = creatures[i];
    }
  }

  private static Random random = new Random();

  /**
   * Uses a Gaussian random generator to pick one the parents within the upper
   * cut. The random generator will favor the values closest to the top, giving
   * the highest scorers the advantage of creating more children.
   * 
   * @return
   */
  private int pickParentPos() {
    int parentPos = creatures.length;
    while (parentPos >= lowerCut) {
      parentPos = (int) (Math.abs(random.nextGaussian()) * upperCut);
    }
    return parentPos;
  }

  /**
   * This is a critical step in the optimization cycle. At this point the
   * creatures are mated with each other and those in the lower cut are replaced
   * (they are killed) and replaced with the children derived from mating of the
   * upper cut. This is the key to the evolutionary algorithm and is essential
   * the survival of the fittest. The worst performers are replaed by a new
   * generation that has a chance at out performing their parents and the
   * previous generation.
   */
  public synchronized void makeNewGeneration() {
    generation++;
    for (int i = lowerCutStart; i < creatures.length; i++) {
      if (!keepRunning) {
        return;
      }
      int a = pickParentPos();
      int b = pickParentPos();
      Creature baby = null;
      if (a != b) {
        baby = new Creature(generation, creatures[a], creatures[b]);
      } else {
        baby = new Creature(generation, creatures[a]);
        baby.tweak();
      }
      // Make sure new baby is original
      int babyHash = baby.hashCode();
      for (int j = 0; j < creatures.length; j++) {
        if (babyHash == creatures[j].hashCode()) {
          baby = null;
          break;
        }
      }
      if (baby != null) {
        creatures[i] = baby;
      }
    }
  }

  /**
   * This returns the current working list. Use getCreaturesCopy to get a copy
   * of this list that is stable and good for use outside of the optimization
   * process.
   * 
   * @return a list of creatures currently in list
   */
  public Creature[] getCreatures() {
    return creatures;
  }

  /**
   * @return a list of creatures from the last optimization score and sort
   */
  public Creature[] getCreaturesCopy() {
    return creaturesCopy;
  }

  private boolean keepRunning = true;

  /**
   * Set to <code>false</code> and this thread will stop running when it reaches
   * a breaking point.
   * 
   * @param b
   *          indicates if this running World instance should continue running
   *          or not
   */
  public void setKeepRunning(boolean b) {
    keepRunning = b;
  }

  private String lastMessage = "";
  private double scoreRate = 0.0;

  /**
   * @return number of creatures scored per second
   */
  public double getScoreRate() {
    return scoreRate;
  }

  public void setScoreRate(double scoreRate) {
    this.scoreRate = scoreRate;
  }

  /**
   * As the optimizer is running it makes a note to where it is at. This note
   * can be helpful to get a view of where the optimizer is currently at.
   * 
   * @return last message noted by optimizer
   */
  public String getLastMessage() {
    return lastMessage;
  }

  @Override
  public void run() {
    lastMessage = "Starting world";
    try {
      scoreAndSort();
      lastMessage = "Initial world created";
      while (keepRunning) {
        lastMessage = "Making new generation";
        makeNewGeneration();
        lastMessage = "Sorting";
        scoreAndSort();
      }
      lastMessage = "Stopping world";
    } catch (Exception e) {
      lastMessage = "Exception occurred: " + e.getMessage();
      e.printStackTrace();
    } finally {
      shutdownScoringPool();
    }
  }

  public boolean isKeepRunning() {
    return keepRunning;
  }
}

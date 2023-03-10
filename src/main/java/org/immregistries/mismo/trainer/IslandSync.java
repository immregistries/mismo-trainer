package org.immregistries.mismo.trainer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.World;

/**
 * IslandSync provides the basic communication support for synchronizing the running world, 
 * which is called an Island, to the wider world stored on the central server. Originally the
 * concept was that there was one running world and that was it. Later this was extended
 * so that each running world was actually an island and could sync up to a central server to 
 * exchange data with other islands in the same world. 
 * @author Nathan Bunker
 *
 */
public class IslandSync extends Thread {
  private World world = null;
  private URL centralUrl = null;
  private int lastSyncedGeneration = 0;

  /**
   * Initializes the IslandSync with the central URL to connect to and the World object
   * that will be used in this interaction. Within a single JVM there is always just one
   * world running representing a single island. 
   * @param world
   * @param centralUrl
   */
 public IslandSync(World world, URL centralUrl) {
    this.world = world;
    this.centralUrl = centralUrl;
  }

  @Override
  public void run() {
    lastMessage = "Island sync started";
    while (keepRunning) {
      update();
      if (keepRunning) {
        synchronized (this) {
          try {
            this.wait(10 * 60 * 1000); // wait 10 minutes, then update again
          } catch (InterruptedException ie) {
            // continue if interrupted
          }
        }
      }
    }
    System.out.println("Shutting down island sync, sending last update to central server");
    update();
  }

  /**
   * Every 10 minutes this method is run to update the central server with the latest
   * creatures and their scores. The central server is the repository for the results
   * of this optimization run. No data is stored locally. 
   */
  private void update() {
    int generation = world.getGeneration();
    if (generation > lastSyncedGeneration && world.getCreaturesCopy() != null) {
      logLastMessage("Syncing generation " + generation);
      try {
        Creature[] creatures = world.getCreaturesCopy();
        int block = 10;
        int j = 0;
        for (int i = 0; i < creatures.length; i += block) {
          j = i + block;
          logLastMessage("Syncing generation " + generation + ", sending creatures " + i + "-" + (j - 1));
          String response = sendUpdate(generation, creatures, i, j);
          if (!response.startsWith("OK")) {
            logLastMessage("Unexpected response from central server: " + response);
            throw new Exception(lastMessage);
          }
        }
        lastSyncedGeneration = generation;
        logLastMessage("Finished Syncing generation " + generation);
      } catch (Exception e) {
        logLastMessage("Exception syncing with central repository: " + e.getMessage());
        e.printStackTrace(System.err);
      }
    }
  }

  /**
   * A convenience method so that this thread can log the last message to give an idea
   * of where this process is currently working at. 
   * @param s
   */
  private void logLastMessage(String s) {
    lastMessage = s;
    if (!keepRunning)
    {
      System.out.println(lastMessage);
    }
  }

  private boolean keepRunning = true;

  /**
   * Set to <code>false</code> to indicate that this thread should stop. The thread
   * will make one last attempt to synchronize with central repository before finishing. 
   * @param b indicates that thread should continue to run
   */
  public void setKeepRunning(boolean b) {
    keepRunning = b;
  }

  private String lastMessage = "";

  /**
   * Use this method to understand what this thread is currently doing. 
   * @return last message logged by thread
  */
  public String getLastMessage() {
    return lastMessage;
  }

  /**
   * Supporting method that sends a chunk of creatures to the central server. 
   * @param generation the current generation from the world
   * @param creatures all the creatures
   * @param start the first creature that should be sent
   * @param end the first creature after the last creature that should be sent
   * @return response from central server, expecting OK if request was received
   * @throws IOException
   */
  private String sendUpdate(int generation, Creature[] creatures, int start, int end) throws IOException {
    URLConnection urlConn;
    DataOutputStream printout;
    InputStreamReader input = null;
    urlConn = centralUrl.openConnection();
    urlConn.setDoInput(true);
    urlConn.setDoOutput(true);
    urlConn.setUseCaches(false);
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    printout = new DataOutputStream(urlConn.getOutputStream());
    StringBuilder sb = new StringBuilder();
    sb.append("action=update&");
    sb.append("worldName=" + URLEncoder.encode(world.getWorldName(), "UTF-8") + "&");
    sb.append("islandName=" + URLEncoder.encode(world.getIslandName(), "UTF-8") + "&");
    sb.append("generation=" + generation + "&");
    sb.append("creatureScript=");
    for (int i = start; i < end && i < creatures.length; i++) {
      Creature creature = creatures[i];
      sb.append(URLEncoder.encode(creature.makeScript() + "\n", "UTF-8"));
    }
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    StringBuilder response = new StringBuilder();
    BufferedReader in = new BufferedReader(input);
    String line;
    while ((line = in.readLine()) != null) {
      response.append(line);
      response.append('\n');
    }
    input.close();
    return response.toString();
  }

  /**
   * Initial query that gets the data needed to populate this island from where it left off last time.
   * @throws IOException
   */
  public void sendQuery() throws IOException {
    URLConnection urlConn;
    DataOutputStream printout;
    InputStreamReader input = null;
    urlConn = centralUrl.openConnection();
    urlConn.setDoInput(true);
    urlConn.setDoOutput(true);
    urlConn.setUseCaches(false);
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    printout = new DataOutputStream(urlConn.getOutputStream());
    StringBuilder sb = new StringBuilder();
    sb.append("action=query&");
    sb.append("worldName=" + URLEncoder.encode(world.getWorldName(), "UTF-8") + "&");
    sb.append("islandName=" + URLEncoder.encode(world.getIslandName(), "UTF-8"));
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    BufferedReader in = new BufferedReader(input);
    String line;
    int maxGeneration = -1;
    Creature[] creatures = world.getCreatures();
    int creaturePos = 0;
    while ((line = in.readLine()) != null && creaturePos < creatures.length) {
      creatures[creaturePos].readScript(line);
      if (maxGeneration < creatures[creaturePos].getGeneration()) {
        maxGeneration = creatures[creaturePos].getGeneration();
      }
      creaturePos++;
    }
    maxGeneration++;
    world.setGeneration(maxGeneration);
    input.close();
  }

  /**
   * Connects with central server and returns the best performing creature within the whole
   * world. This will be used as the base case when starting a new generation of 
   * creatures. 
   * @param worldName name of the world to pull from
   * @param centralUrl points to where the central server is
   * @return a creature script
   * @throws IOException
   */
  public static String requestStartScript(String worldName, URL centralUrl) throws IOException {
    URLConnection urlConn;
    DataOutputStream printout;
    InputStreamReader input = null;
    urlConn = centralUrl.openConnection();
    urlConn.setDoInput(true);
    urlConn.setDoOutput(true);
    urlConn.setUseCaches(false);
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    printout = new DataOutputStream(urlConn.getOutputStream());
    StringBuilder sb = new StringBuilder();
    sb.append("action=requestStartScript&");
    sb.append("worldName=" + URLEncoder.encode(worldName, "UTF-8") + "&");
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    try {
      BufferedReader in = new BufferedReader(input);
      return in.readLine();
    } finally {
      input.close();
    }
  }

  /**
   * Method for requesting that the local island be seeded with a random creature
   * from some other island. This method may be used to cross-pollinate this island. 
   * @throws IOException
   */
  public void requestSeed() throws IOException {
    URLConnection urlConn;
    DataOutputStream printout;
    InputStreamReader input = null;
    urlConn = centralUrl.openConnection();
    urlConn.setDoInput(true);
    urlConn.setDoOutput(true);
    urlConn.setUseCaches(false);
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    printout = new DataOutputStream(urlConn.getOutputStream());
    StringBuilder sb = new StringBuilder();
    sb.append("action=seed&");
    sb.append("worldName=" + URLEncoder.encode(world.getWorldName(), "UTF-8") + "&");
    sb.append("islandName=" + URLEncoder.encode(world.getIslandName(), "UTF-8"));
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    BufferedReader in = new BufferedReader(input);
    String line;
    Creature seed = null;
    if ((line = in.readLine()) != null) {
      seed = new Creature(world, line);
    }
    world.plantSeed(seed);
    input.close();
  }

}

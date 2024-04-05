package org.immregistries.mismo.trainer;

import static org.immregistries.mismo.trainer.servlet.CentralServlet.ACTION_QUERY;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.ACTION_REQUEST_START_SCRIPT;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.ACTION_UPDATE;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.PARAM_ACTION;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.PARAM_CONFIGURATION_SCRIPT;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.PARAM_ISLAND_NAME;
import static org.immregistries.mismo.trainer.servlet.CentralServlet.PARAM_WORLD_NAME;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.immregistries.mismo.match.StringUtils;
import org.immregistries.mismo.match.model.Configuration;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.World;
import org.immregistries.mismo.trainer.servlet.CentralServlet;

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
    lastMessage = "Island sync ready";
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
        logLastMessage("Syncing generation " + generation + ", sending best creature ");
        String response = sendUpdate(generation, creatures[0]);
        if (!response.startsWith("OK")) {
          logLastMessage("Unexpected response from central server: " + response);
          throw new Exception(lastMessage);
        }
        lastSyncedGeneration = generation;
        logLastMessage("Last synced generation " + generation);
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
  private String sendUpdate(int generation, Creature creature) throws IOException {
    creature.getPatientCompare().getConfiguration().createConfigurationScript(); // Make sure we have the latest script before sending
    String configurationScript = creature.getPatientCompare().getConfiguration().getConfigurationScript();
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
    sb.append(PARAM_ACTION + "=" + ACTION_UPDATE + "&");
    sb.append(PARAM_WORLD_NAME + "=" + URLEncoder.encode(world.getWorldName(), "UTF-8") + "&");
    sb.append(PARAM_ISLAND_NAME + "=" + URLEncoder.encode(world.getIslandName(), "UTF-8") + "&");
    sb.append(PARAM_CONFIGURATION_SCRIPT + "=");
    sb.append(URLEncoder.encode(configurationScript, "UTF-8"));
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
    sb.append(PARAM_ACTION + "=" + ACTION_QUERY + "&");
    sb.append(PARAM_WORLD_NAME + "=" + URLEncoder.encode(world.getWorldName(), "UTF-8") + "&");
    sb.append(PARAM_ISLAND_NAME + "=" + URLEncoder.encode(world.getIslandName(), "UTF-8"));
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    BufferedReader in = new BufferedReader(input);
    String line = in.readLine();
    if (line != null && !line.equals(CentralServlet.RESULT_NOT_FOUND)) {
      StringBuilder configurationScript = new StringBuilder();
      Creature[] creatures = world.getCreatures();
      while (line != null ) {
        configurationScript.append(line + "\n");
        line = in.readLine();
      }
      creatures[0].readScript(line);
      world.setGeneration(creatures[0].getGeneration());
      input.close();
    }
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
  public static String requestStartScript(String worldName, String islandName, URL centralUrl) throws IOException {
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
    sb.append(PARAM_ACTION + "=" + ACTION_REQUEST_START_SCRIPT + "&");
    sb.append(PARAM_WORLD_NAME + "=" + URLEncoder.encode(worldName, "UTF-8") + "&");
    sb.append(PARAM_ISLAND_NAME + "=" + URLEncoder.encode(islandName, "UTF-8"));
    printout.writeBytes(sb.toString());
    printout.flush();
    printout.close();
    input = new InputStreamReader(urlConn.getInputStream());
    StringBuilder configurationScript = new StringBuilder();
    try {
      BufferedReader in = new BufferedReader(input);
      String line;
      while ((line = in.readLine()) != null) {
        if (StringUtils.isNotEmpty(line)) {
          configurationScript.append(line + "\n");
        }
      }
    } finally {
      input.close();
    }
    return configurationScript.toString();
  }

}

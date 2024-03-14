package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.Island;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.World;

/**
 * This is the central servlet that the remote island threads access to read and
 * store their data and report on progress.
 * 
 * @author Nathan Bunker
 * 
 */
public class CentralServlet extends HomeServlet
{

  private File dataStoreDir = null;

  @Override
  public void init() throws ServletException {
    String dataStoreDirString = getServletConfig().getInitParameter("dataStoreDir");
    if (dataStoreDirString != null && dataStoreDirString.length() > 0) {
      dataStoreDir = new File(dataStoreDirString);
      if (!dataStoreDir.exists()) {
        dataStoreDir = null;
      }
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute("user");
    Session dataSession = (Session) session.getAttribute("dataSession");

    if (user == null) {
      RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
      dispatcher.forward(req, resp);
      return;
    }

    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Central Servlet</h1>");

      if (dataStoreDir != null) {
        DecimalFormat decimalFormat = new DecimalFormat("#0.0000000");
        List<MatchItem> matchItemList = getMatchItemList();

        File[] worldDirs = dataStoreDir.listFiles();
        for (File worldDir : worldDirs) {
          if (worldDir.isDirectory()) {
            World world = new World(0, worldDir.getName(), "", null);
            world.setMatchItemList(matchItemList);
            out.println("<h2>World " + worldDir.getName() + "</h2>");
            out.println("<table border=\"1\" cellspacing=\"0\">");
            out.println("  <tr>");
            out.println("    <th>Island</th>");
            out.println("    <th>File Name</th>");
            out.println("    <th>Generation</th>");
            out.println("    <th>Score</th>");
            out.println("    <th>Select</th>");
            out.println("  </tr>");
            File[] islandDirs = worldDir.listFiles();
            for (File islandDir : islandDirs) {
              if (islandDir.isDirectory()) {
                world.setIslandName(islandDir.getName());
                File creatureFile = findLatestFile(islandDir);
                if (creatureFile != null) {
                  BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(creatureFile)));
                  String line = in.readLine();
                  if (line != null) {
                    Creature creature = new Creature(world, line);
                    creature.score();
                    out.println("      <tr>");
                    out.println("        <td>" + islandDir.getName() + "</td>");
                    out.println("        <td>" + creatureFile.getName() + "</td>");
                    out.println("        <td>" + creature.getGeneration() + "</td>");
                    out.println("        <td>" + decimalFormat.format((creature.getScore() * 100.0)) + "</td>");
                    out.println("        <td>");
                    out.println("          <form action=\"WeightSetServlet\" method=\"POST\"> ");
                    out.println("            <input type=\"hidden\" name=\""
                        + TestMatchingServlet.PARAM_CREATURE_SCRIPT + "\" value=\"" + line + "\"/>");
                    out.println("          <input type=\"submit\" name=\"submit\" value=\"Select\"/>");
                    out.println("          </form>");
                    out.println("        </td>");
                    out.println("      </tr>");
                  }
                  in.close();
                }
              }
            }
            out.println("    </table>");
          }
        }
        out.println("<h3>How To Run Islands</h2>");
        out.println("<p>To run this via the command line, follow these steps:</p>");
        out.println("<ol>");
        out.println("  <li>Startup local CentralServlet or obtain URL to CentralServlet you will connect to.</li>");
        out.println("  <li>Identify source file for you tests. The default tests are currently checked into the project.</li>");
        out.println("  <li>Modify and use this command line from the root of the project:</li>");
        out.println("</ol>");
        out.println("<p>Example command:</p>");
        out.println("<code>mvn exec:java -Dexec.mainClass=\"org.immregistries.pm.Island\" -Dexec.args=\"http://localhost:8286/CentralServlet src/main/java/org.immregistries.pm/servlet/MIIS-C.txt\"</code>");
      } else {
        out.println("<p>Local data store is not configure properly so central servlet is not operating.</p>");
      }
      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.print("<pre>");
      e.printStackTrace(out);
      out.print("</pre>");
    } finally {
      out.close();
    }
  }

  private List<MatchItem> getMatchItemList() throws IOException {
    List<MatchItem> matchTestCaseList;
    {
      BufferedReader in = null;
      in = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/MIIS-E3.txt")));
      matchTestCaseList = Island.readSourceFile(in);
    }
    return matchTestCaseList;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String responseMessage = "Problem";
    if (dataStoreDir != null) {
      String action = req.getParameter("action");
      if (action == null) {
        action = "update";
      }
      if (action.equals("update")) {
        String worldName = req.getParameter("worldName");
        String islandName = req.getParameter("islandName");
        int generation = Integer.parseInt(req.getParameter("generation"));
        String creatureScript = req.getParameter("creatureScript");
        File islandDir = new File(dataStoreDir, "/" + worldName + "/" + islandName);
        if (!islandDir.exists()) {
          islandDir.mkdirs();
        }
        File output = new File(islandDir, "gen" + generation + ".dat");
        PrintWriter fileOut = new PrintWriter(new FileWriter(output, true));
        fileOut.print(creatureScript);
        fileOut.close();
        // Delete all but the last two files
        int skipCount = 2;
        while (generation > 0) {
          generation--;
          output = new File(islandDir, "gen" + generation + ".dat");
          if (output.exists()) {
            if (skipCount == 0) {
              output.delete();
            } else {
              skipCount--;
            }
          }
        }
        responseMessage = "OK";
        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        out.println(responseMessage);
        out.close();
      } else if (action.equals("query")) {

        String worldName = req.getParameter("worldName");
        String islandName = req.getParameter("islandName");
        File islandDir = new File(dataStoreDir, "/" + worldName + "/" + islandName);
        File selectedFile = findLatestFile(islandDir);
        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        if (selectedFile != null) {
          BufferedReader in = new BufferedReader(new FileReader(selectedFile));
          String line;
          while ((line = in.readLine()) != null) {
            out.println(line);
          }
        }
        out.close();
      } else if (action.equals("requestStartScript")) {
        String worldName = req.getParameter("worldName");
        File worldDir = new File(dataStoreDir, worldName);
        Creature maxCreature = null;
        if (worldDir.exists() && worldDir.isDirectory()) {
          World world = new World(0, worldDir.getName(), "", null);
          world.setMatchItemList(getMatchItemList());
          for (File islandDir : worldDir.listFiles()) {
            if (islandDir.isDirectory()) {
              world.setIslandName(islandDir.getName());
              File selectedFile = findLatestFile(islandDir);
              if (selectedFile != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(selectedFile)));
                String line = in.readLine();
                if (line != null) {
                  Creature creature = new Creature(world, line);
                  creature.score();
                  if (maxCreature == null || maxCreature.getScore() < creature.getScore()) {
                    maxCreature = creature;
                  }
                }
              }
            }
          }
          resp.setContentType("text/plain");
          PrintWriter out = new PrintWriter(resp.getOutputStream());
          out.println(maxCreature == null ? "" : maxCreature.makeScript());
          out.close();
        }
      } else if (action.equals("seed")) {
        String worldName = req.getParameter("worldName");
        String islandName = req.getParameter("islandName");
        File worldDir = new File(dataStoreDir, worldName);
        List<Creature> creatureList = new ArrayList<Creature>();
        Creature chiefCreature = null;
        if (worldDir.exists() && worldDir.isFile()) {
          World world = new World(0, worldDir.getName(), "", null);
          world.setMatchItemList(getMatchItemList());
          for (File islandDir : worldDir.listFiles()) {
            if (islandDir.isDirectory()) {
              world.setIslandName(islandDir.getName());
              File selectedFile = findLatestFile(islandDir);
              if (selectedFile != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(selectedFile)));
                String line = in.readLine();
                if (line != null) {
                  Creature creature = new Creature(world, line);
                  creature.score();
                  if (islandDir.getName().equalsIgnoreCase(islandName)) {
                    chiefCreature = creature;
                  } else {
                    creatureList.add(creature);
                  }
                }
              }
            }
          }
          if (chiefCreature != null && creatureList.size() > 0) {
            int pos = (int) System.currentTimeMillis() % creatureList.size();
            resp.setContentType("text/plain");
            PrintWriter out = new PrintWriter(resp.getOutputStream());
            out.println(creatureList.get(pos).makeScript());
            out.close();
          }
        }
      }
    }
  }

  private File findLatestFile(File islandDir) {
    File selectedFile = null;
    if (islandDir.exists()) {
      File[] files = islandDir.listFiles();
      int maxGeneration = 0;
      for (File file : files) {
        if (file.isFile()) {
          String filename = file.getName();
          if (filename.startsWith("gen") && filename.endsWith(".dat")) {
            int generation = Integer.parseInt(file.getName().substring(3, filename.length() - 4));
            if (generation > maxGeneration) {
              maxGeneration = generation;
            }
          }
        }
      }
      if (maxGeneration > 0) {
        selectedFile = new File(islandDir, "gen" + maxGeneration + ".dat");
      }
    }
    return selectedFile;
  }

  // dataStoreDir
}

package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.MatchItem;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.User;

@MultipartConfig
public class TestSetUploadServlet extends TestSetServlet {

  private static String uploadDirString = "/temp";

  public static final String PARAM_MATCH_SET_ID = "matchSetId";
  public static final String PARAM_DATA_SOURCE = "dataSource";
  public static final String PARAM_DATA_FILE = "dataFile";

  @Override
  public void init() throws ServletException {
    uploadDirString = getInitParameter("uploadDir");

  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // Need to read a file upload and then proces it. 
    // Here are the items on the form:
    //   PARAM_MATCH_SET_ID
    //   PARAM_DATA_SOURCE
    //   PARAM_DATA_FILE
    // Need to save the data file to a temporary directory and capture the matchSetId and the dataSource for later use.
    // Then need to read the file and process it into a list of MatchItem objects.
    // Then need to save the MatchItem objects to the database.
    // Then need to redirect to the TestSetServlet with the matchSetId and a message.
    // Here is the code to read the file upload and process it into a list of MatchItem objects.

    
    setup(req, resp);
    
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute("user");
    Session dataSession = (Session) session.getAttribute("dataSession");

    String dataSource = req.getParameter(PARAM_DATA_SOURCE);
    MatchSet matchSetSelected = OrgScope.loadMatchSet(dataSession,
        Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID)), user);
    String message = null;

    if (matchSetSelected == null) {
      message = "Match set not found";
    } else if (!OrgScope.canEditCases(matchSetSelected, user)) {
      // matchSetSelected is readable (possibly a template owned by another organization,
      // §4.1) but not editable -- uploading to it would silently write into another
      // organization's data. Copy it to this organization first. It may also be editable but
      // currently Approved (v2-roadmap.md §10) -- move it out of that status first.
      message = OrgScope.isEditable(matchSetSelected, user)
          ? "Cannot upload data while this Test Set is Approved. Change its status first."
          : "Cannot upload data to a template match set you do not own. Copy it to your"
              + " organization first.";
    } else if (dataSource.equals("")) {
      message = "Data source is required";
    } else {
      Part filePart = req.getPart(PARAM_DATA_FILE);
      InputStream inputStream = filePart.getInputStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
      List<MatchItem> matchItemList = parseMatchItems(in);
      in.close();
      Transaction transaction = dataSession.beginTransaction();
      Date updateDate = new Date();
      matchSetSelected.setUpdatedByUser(user);
      matchSetSelected.setUpdatedAt(updateDate);
      dataSession.update(matchSetSelected);
      for (MatchItem matchItem : matchItemList) {
        Query query = dataSession.createQuery("from MatchItem where matchSet = ? and label = ?");
        query.setParameter(0, matchSetSelected);
        query.setParameter(1, matchItem.getLabel());
        List<MatchItem> matchItemMatchList = query.list();
        if (matchItemMatchList.size() > 0) {
          MatchItem saveMatchItem = matchItemMatchList.get(0);
          saveMatchItem.setLabel(matchItem.getLabel());
          saveMatchItem.setExpectStatus(matchItem.getExpectStatus());
          saveMatchItem.setPatientDataA(matchItem.getPatientDataA());
          saveMatchItem.setPatientDataB(matchItem.getPatientDataB());
          saveMatchItem.setDescription(matchItem.getDescription());
          saveMatchItem.setUpdatedByUser(user);
          saveMatchItem.setUpdatedAt(updateDate);
          dataSession.update(saveMatchItem);
        } else {
          matchItem.setMatchSet(matchSetSelected);
          matchItem.setDataSource(dataSource);
          matchItem.setCreatedByUser(user);
          matchItem.setUpdatedByUser(user);
          matchItem.setCreatedAt(updateDate);
          matchItem.setUpdatedAt(updateDate);
          matchItem.setOriginalExpectStatus(matchItem.getExpectStatus());
          matchItem.setProvenanceType(MatchItem.PROVENANCE_IMPORTED);
          dataSession.save(matchItem);
        }
      }
      transaction.commit();
      message = matchItemList.size() + " match test cases loaded";
    }

    teardown(req, resp);

    int matchSetId = matchSetSelected == null ? Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID))
        : matchSetSelected.getMatchSetId();
    String newUrl = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetId
        + "&" + PARAM_MESSAGE + "=" + URLEncoder.encode(message, "UTF-8");
    resp.sendRedirect(newUrl);
  }

  /**
   * Parses the {@code TEST:}/{@code EXPECT:}/{@code PATIENT A:}/{@code PATIENT B:}/
   * {@code DESCRIPTION:} flat-file format into unsaved {@code MatchItem} rows. Shared with
   * {@link org.immregistries.mismo.trainer.TemplateDataBootstrap}, which reuses this exact
   * parser to load {@code legacy-test-data/AIRA-D.txt} into the AIRA template match set
   * (v2-roadmap.md §8) instead of re-implementing it.
   */
  public static List<MatchItem> parseMatchItems(BufferedReader in) throws IOException {
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

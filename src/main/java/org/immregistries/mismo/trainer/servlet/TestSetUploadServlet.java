package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.MatchSet;
import org.immregistries.mismo.match.model.User;

public class TestSetUploadServlet extends TestSetServlet {

  private static String uploadDirString = "/temp";

  @Override
  public void init() throws ServletException {
    uploadDirString = getInitParameter("uploadDir");

  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
//    IFileCleaner fileCleaner = new IFileCleaner() {
//
//      public void track(File file, Object marker, FileDeleteStrategy deleteStrategy) {
//        // TODO Auto-generated method stub
//
//      }

//      public void track(File file, Object marker) {
//        // TODO Auto-generated method stub
//
//      }
//
//      public void destroy() {
//        // TODO Auto-generated method stub
//
//      }
//    };

    // DiskFileItemFactory fileItemFactory = new DiskFileItemFactory(fileCleaner);
    //    fileItemFactory.setSizeThreshold(100 * 1024 * 1024); // 100 MB
    //    File uploadDir = new File(uploadDirString);
    //    if (!uploadDir.exists()) {
    //      throw new IllegalArgumentException("Upload directory not found, unable to upload");
    //    }
    //
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute("user");
    Session dataSession = (Session) session.getAttribute("dataSession");

    String dataSource = "";
    //    fileItemFactory.setRepository(uploadDir);
    // ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
    File file = null;
    MatchSet matchSetSelected = null;
    try {
      //      List<FileItem> items = uploadHandler.parseRequest(req);
      //      for (FileItem item : items) {
      //        /*
      //         * Handle Form Fields.
      //         */
      //        if (item.isFormField()) {
      //          if (item.getFieldName().equals(PARAM_DATA_SOURCE)) {
      //            dataSource = item.getString();
      //          } else if (item.getFieldName().equals(PARAM_MATCH_SET_ID)) {
      //            matchSetSelected = (MatchSet) dataSession.get(MatchSet.class, Integer.parseInt(item.getString()));
      //          }
      //        } else {
      //          file = File.createTempFile("upload", ".txt");
      //          item.write(file);
      //        }
      //      }
    } catch (Exception ex) {
      throw new ServletException("Unable to upload file", ex);
    }

    String message;

    if (dataSource.equals("")) {
      message = "Data source is required";
    } else {

      BufferedReader in = new BufferedReader(new FileReader(file));
      List<MatchItem> matchItemList = new ArrayList<MatchItem>();
      {
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
      }
      in.close();
      Transaction transaction = dataSession.beginTransaction();
      Date updateDate = new Date();
      matchSetSelected.setUpdateDate(updateDate);
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
          dataSession.update(saveMatchItem);
        } else {
          matchItem.setMatchSet(matchSetSelected);
          matchItem.setUser(user);
          matchItem.setUpdateDate(updateDate);
          matchItem.setDataSource(dataSource);
          dataSession.save(matchItem);
        }
      }
      transaction.commit();
      message = matchItemList.size() + " match test cases loaded";
    }
    if (file != null) {
      file.delete();
    }

    String newUrl = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId()
        + "&" + PARAM_MESSAGE + "=" + URLEncoder.encode(message, "UTF-8");
    resp.sendRedirect(newUrl);
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

package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.trainer.model.User;

/**
 * Allows for download of patient data in HL7 format for the CDC project.
 *
 * @author Nathan Bunker
 */
public class DownloadHl7Servlet extends HomeServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      HomeServlet.doHeader(out, req, user, null);
      out.println("    <h1>Download HL7 Servlet</h1>");

      SessionFactory factory = getSessionFactory();
      Session dataSession = factory.openSession();

      Query query = dataSession.createQuery("from Patient");
      List<Patient> patientList = query.list();

      out.println("    <pre>");
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
      String today = sdf.format(new Date());
      int count = 0;
      for (Patient patient : patientList) {
        out.print(
            "MSH|^~\\&|||||" + today + "||VXU^V04^VXU_V04|" + today + "-" + count + "|P|2.5.1|\r");
        out.print(
            "PID|1||"
                + patient.getPatientId()
                + "^^^PM^MR||"
                + patient.getNameLast()
                + "^"
                + patient.getNameFirst()
                + "^"
                + patient.getNameMiddle()
                + "^"
                + patient.getNameSuffix()
                + "^^^L"
                + "|"
                + patient.getMotherMaidenName()
                + "|"
                + patient.getBirthDate()
                + "|"
                + patient.getGender()
                + "|\r");
        if (!patient.getMotherNameFirst().equals("") || !patient.getMotherNameLast().equals("")) {
          out.print(
              "NK1|1|"
                  + patient.getMotherNameLast()
                  + "^"
                  + patient.getMotherNameFirst()
                  + "^"
                  + patient.getMotherNameMiddle()
                  + "|MTH^Mother^HL70063|\r");
        }
        if (!patient.getVacCode().equals("")) {
          out.print(
              "RXA|0|1|"
                  + patient.getVacDate()
                  + "|"
                  + patient.getVacDate()
                  + "|"
                  + patient.getVacCode()
                  + "^^CVX|999|||01^Historical^NIP0001||||||||"
                  + patient.getVacMfr()
                  + "^^MVX||||A|\r");
        }
      }

      dataSession.close();

      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      out.print("<pre>");
      e.printStackTrace(out);
      out.print("</pre>");
    } finally {
      out.close();
      teardown(req, resp);
    }
  }

  private static SessionFactory factory;

  /**
   * Establishes session factory singleton.
   */
  public static SessionFactory getSessionFactory() {
    if (factory == null) {

      factory = new Configuration().configure().buildSessionFactory();
    }
    return factory;
  }

}

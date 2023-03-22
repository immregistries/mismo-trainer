package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;

/**
 * Creates a list of patient records, some that match and some that don't, and
 * places them in a script with a suggested match criteria. This allows for
 * rapid creation of test data.
 * 
 * @author Nathan Bunker
 * 
 */
public class ConvertDataServlet extends HomeServlet
{

  private static final String BIRTH_STATUS = "birthStatus";
  private static final String BIRTH_ORDER = "birthOrder";
  private static final String BIRTH_TYPE = "birthType";
  private static final String CAREGIVER_MAIDEN_NAME = "caregiverMaidenName";
  private static final String CAREGIVER_NAME_FIRST = "caregiverNameFirst";
  private static final String CAREGIVER_NAME_LAST = "caregiverNameLast";
  private static final String HOME_PHONE_NUMBER = "homePhoneNumber";
  private static final String ZIP = "zip";
  private static final String STATE = "state";
  private static final String CITY = "city";
  private static final String ADDRESS_LINE_1 = "adddressLine1";
  private static final String ADDRESS_LINE_2 = "adddressLine2";
  private static final String ZIP2 = "zip2";
  private static final String STATE2 = "state2";
  private static final String CITY2 = "city2";
  private static final String ADDRESS2_LINE_1 = "adddress2Line1";
  private static final String ADDRESS2_LINE_2 = "adddress2Line2";
  private static final String MRN = "mrn";
  private static final String ORGANIZATION = "organization";
  private static final String GENDER = "gender";
  private static final String DATE_OF_BIRTH = "dateOfBirth";
  private static final String LAST_NAME = "lastName";
  private static final String MIDDLE_NAME = "middleName";
  private static final String FIRST_NAME = "firstName";
  private static final String PATIENT_ID_COL = "patientIdCol";
  private static final String PATIENT_AB = "patientAB";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }

  private static final String[] EXCEL_COLUMNS = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
      "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI",
      "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ" };

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    response.setContentType("text/html");
    PrintWriter out = new PrintWriter(response.getOutputStream());
    HttpSession session = request.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      HomeServlet.doHeader(out, user, null);
      out.println("    <script>");
      out.println("      function toggleLayer(whichLayer) ");
      out.println("      {");
      out.println("        var elem, vis;");
      out.println("        if (document.getElementById) ");
      out.println("          elem = document.getElementById(whichLayer);");
      out.println("        else if (document.all) ");
      out.println("          elem = document.all[whichLayer] ");
      out.println("        else if (document.layers) ");
      out.println("          elem = document.layers[whichLayer]");
      out.println("        vis = elem.style;");
      out.println("        if (vis.display == '' && elem.offsetWidth != undefined && elem.offsetHeight != undefined) ");
      out.println("          vis.display = (elem.offsetWidth != 0 && elem.offsetHeight != 0) ? 'block' : 'none';");
      out.println("        vis.display = (vis.display == '' || vis.display == 'block') ? 'none' : 'block';");
      out.println("      }");
      out.println("    </script>");
      out.println("    <h1>Convert Data</h1>");

      String data = request.getParameter("data");
      if (data != null) {
        out.println("<p>Converted Data</p>");
        out.println("<pre>");
        Patient patientA = null;
        Patient patientB = null;
        Patient patient = null;
        StringReader stringReader = new StringReader(data);
        BufferedReader in = new BufferedReader(stringReader);
        String line;
        while ((line = in.readLine()) != null) {
          String parts[] = line.split("\\t");
          String type = readPart(PATIENT_AB, parts, request);
          if (type.equals("Patient A")) {
            patientA = new Patient();
            patient = patientA;
          } else if (type.equals("Patient B")) {
            patientB = new Patient();
            patient = patientB;
          }
          if (type.equals("Patient A") || type.equals("Patient B")) {
            patient.setPatientId(Integer.parseInt(readPart(PATIENT_ID_COL, parts, request)));
            patient.setNameFirst(readPart(FIRST_NAME, parts, request));
            patient.setNameMiddle(readPart(MIDDLE_NAME, parts, request));
            patient.setNameLast(readPart(LAST_NAME, parts, request));
            patient.setBirthDate(readPart(DATE_OF_BIRTH, parts, request));
            //patient.setGender(readPart(GENDER, parts, request));
            // patient.setOr(readPart(ORGANIZATION, parts, request));
            //patient.setMrns(readPart(MRN, parts, request));
            patient.setAddressStreet1(readPart(ADDRESS_LINE_1, parts, request));
            patient.setAddressStreet2(readPart(ADDRESS_LINE_2, parts, request));
            patient.setAddressCity(readPart(CITY, parts, request));
            patient.setAddressState(readPart(STATE, parts, request));
            patient.setAddressZip(readPart(ZIP, parts, request));
            //patient.setAddress2Street1(readPart(ADDRESS2_LINE_1, parts, request));
            //patient.setAddress2Street2(readPart(ADDRESS2_LINE_2, parts, request));
            //patient.setAddress2City(readPart(CITY2, parts, request));
            //patient.setAddress2State(readPart(STATE2, parts, request));
            //patient.setAddress2Zip(readPart(ZIP2, parts, request));
            patient.setPhone(readPart(HOME_PHONE_NUMBER, parts, request));
            //patient.setGuardianNameLast(readPart(CAREGIVER_NAME_LAST, parts, request));
            //patient.setGuardianNameFirst(readPart(CAREGIVER_NAME_FIRST, parts, request));
            //patient.setMotherMaidenName(readPart(CAREGIVER_MAIDEN_NAME, parts, request));
            //patient.setBirthType(readPart(BIRTH_TYPE, parts, request));
            //patient.setBirthOrder(readPart(BIRTH_ORDER, parts, request));
            //patient.setBirthStatus(readPart(BIRTH_STATUS, parts, request));
            if (type.equals("Patient B")) {
              out.println("TEST: " + patientA.getPatientId() + "-" + patientB.getPatientId());
              out.println("EXPECT: ");
              out.println("PATIENT A: " + patientA.getValues());
              out.println("PATIENT B: " + patientB.getValues());
            }
          }
        }
        out.println("</pre>");
      }

      out.println("<p>Convert Data Form</p>");
      out.println("    <form action=\"ConvertDataServlet\" method=\"POST\">");
      out.println("    <table border=\"0\">");
      printColumnSelection(out, 0, "Patient Id", PATIENT_ID_COL);
      printColumnSelection(out, 1, "Patient A/B", PATIENT_AB);
      printColumnSelection(out, 2, "First Name", FIRST_NAME);
      printColumnSelection(out, 3, "Middle Name", MIDDLE_NAME);
      printColumnSelection(out, 4, "Last Name", LAST_NAME);
      printColumnSelection(out, 5, "Date of Birth", DATE_OF_BIRTH);
      //printColumnSelection(out, 6, "Gender", GENDER);
      //printColumnSelection(out, 7, "Organization", ORGANIZATION);
      //printColumnSelection(out, 8, "MRN", MRN);
      printColumnSelection(out, 6, "Address Line 1", ADDRESS_LINE_1);
      printColumnSelection(out, 7, "Address Line 2", ADDRESS_LINE_2);
      printColumnSelection(out, 8, "City", CITY);
      printColumnSelection(out, 9, "State", STATE);
      printColumnSelection(out, 10, "Zip", ZIP);
      //printColumnSelection(out, 14, "2nd Address Line 1", ADDRESS2_LINE_1);
      //printColumnSelection(out, 15, "2nd Address Line 2", ADDRESS2_LINE_2);
      //printColumnSelection(out, 16, "2nd City", CITY2);
      //printColumnSelection(out, 17, "2nd State", STATE2);
      //printColumnSelection(out, 18, "2nd Zip", ZIP2);
      printColumnSelection(out, 11, "Home Phone Number", HOME_PHONE_NUMBER);
      //printColumnSelection(out, 20, "Caregiver Name Last", CAREGIVER_NAME_LAST);
      //printColumnSelection(out, 21, "Caregiver Name First", CAREGIVER_NAME_FIRST);
      //printColumnSelection(out, 22, "Caregiver Maiden Name", CAREGIVER_MAIDEN_NAME);
      //printColumnSelection(out, 23, "Birth Type", BIRTH_TYPE);
      //printColumnSelection(out, 24, "Birth Order", BIRTH_ORDER);
      //printColumnSelection(out, 25, "Birth Status", BIRTH_STATUS);
      out.println("    </table>");
      out.println("    Data<br/>");
      out.println("   <textarea name=\"data\" cols=\"70\" rows=\"5\" wrap=\"off\"></textarea><br/>");
      out.println("   <input type=\"submit\" name=\"submit\" value=\"Convert\"/>");
      out.println("    </form>");

      HomeServlet.doFooter(out, user);
    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    }
    out.close();

  }

  private String readPart(String field, String[] parts, HttpServletRequest request) {
    return readPart(Integer.parseInt(request.getParameter(field)), parts);
  }

  private static String readPart(int pos, String[] parts) {
    if (parts != null && parts.length > pos) {
      if (parts[pos] != null) {
        return parts[pos];
      }
    }
    return "";
  }

  private void printColumnSelection(PrintWriter out, int fieldPos, String field, String fieldName) {
    out.println("      <tr>");
    out.println("        <td>" + field + "</td>");
    out.println("        <td> ");
    printExcelColumnDropDown(out, fieldPos, fieldName);
    out.println("        </td>");
    out.println("      </tr>");
  }

  private void printExcelColumnDropDown(PrintWriter out, int option, String name) {
    out.println("          <select name=\"" + name + "\">");
    for (int i = 0; i < EXCEL_COLUMNS.length; i++) {
      if (i == option) {
        out.println("            <option value=\"" + option + "\" selected>" + EXCEL_COLUMNS[i] + "</option>");
      } else {
        out.println("            <option value=\"" + option + "\">" + EXCEL_COLUMNS[i] + "</option>");
      }
    }
    out.println("          </select>");
  }

}

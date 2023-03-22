package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.random.Transformer;
import org.immregistries.mismo.trainer.random.Typest;

/**
 * Support generating two pairs of random records. One pair represents the same
 * patient and ideally would match. The second pair represents a patient with
 * similar characteristics but that is not a match.
 * 
 * @author Nathan Bunker
 * 
 */
public class RandomServlet extends HomeServlet
{

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }

//  private static String[] displayFields = { "nameFirst", "nameMiddle", "nameLast", "nameSuffix", "birthDate", "gender",
//      "guardianNameFirst", "guardianNameLast", "motherMaidenName", "ssn", "medicaid", "phone", "addressStreet1",
//      "addressStreet2", "addressCity", "addressState", "addressZip", "birthType", "birthStatus", "birthOrder" };
  private static String[] displayFields = { "nameFirst", "nameMiddle", "nameLast", "birthDate", "addressStreet1", "addressZip"};

  private static Map<String, String> displayFieldLabels = new HashMap<String, String>();

  static {
    displayFieldLabels.put("nameFirst", "First Name");
    displayFieldLabels.put("nameMiddle", "Middle Name");
    displayFieldLabels.put("nameLast", "Last Name");
    displayFieldLabels.put("nameSuffix", "Suffix");
    displayFieldLabels.put("birthDate", "Birth Date");
    displayFieldLabels.put("gender", "Gender");
    displayFieldLabels.put("guardianNameFirst", "Parent First");
    displayFieldLabels.put("guardianNameLast", "Parent Last");
    displayFieldLabels.put("motherMaidenName", "Mother Maiden Name");
    displayFieldLabels.put("ssn", "SSN");
    displayFieldLabels.put("medicaid", "Medicaid Id");
    displayFieldLabels.put("phone", "Phone");
    displayFieldLabels.put("addressStreet1", "Address 1");
    displayFieldLabels.put("addressStreet2", "Address 2");
    displayFieldLabels.put("addressCity", "City");
    displayFieldLabels.put("addressState", "State");
    displayFieldLabels.put("addressZip", "Zip");
    displayFieldLabels.put("birthType", "Birth Type");
    displayFieldLabels.put("birthStatus", "Birth Status");
    displayFieldLabels.put("birthOrder", "Birth Order");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("text/html");
    PrintWriter out = new PrintWriter(response.getOutputStream());
    HttpSession session = request.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      String typeAString = request.getParameter("typeA");
      String typeBString = request.getParameter("typeB");
      String typeCString = request.getParameter("typeC");
      Typest.Type typeA = Typest.Type.IDEAL;
      Typest.Type typeB = Typest.Type.IDEAL;
      Typest.Type typeC = Typest.Type.IDEAL;
      if (typeAString != null) {
        typeA = Typest.Type.valueOf(typeAString);
      }
      if (typeBString != null) {
        typeB = Typest.Type.valueOf(typeBString);
      }
      if (typeCString != null) {
        typeC = Typest.Type.valueOf(typeCString);
      }

      String conditionAString = request.getParameter("conditionA");
      String conditionBString = request.getParameter("conditionB");
      String conditionCString = request.getParameter("conditionC");
      Typest.Condition conditionA = null;
      Typest.Condition conditionB = null;
      Typest.Condition conditionC = null;
      if (conditionAString != null && !conditionAString.equals("")) {
        conditionA = Typest.Condition.valueOf(conditionAString);
      }
      if (conditionBString != null && !conditionBString.equals("")) {
        conditionB = Typest.Condition.valueOf(conditionBString);
      }
      if (conditionCString != null && !conditionCString.equals("")) {
        conditionC = Typest.Condition.valueOf(conditionCString);
      }

      boolean showScores = true;
      if (request.getParameter("showScores") != null && request.getParameter("showScores").equals("false")) {
        showScores = false;
      }

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
      out.println("    <h1>Random Patient</h1>");
      Transformer transformer = new Transformer();
      Patient patientA = null;
      Patient patientB = null;
      Patient patientC = null;
      Patient patient = transformer.createPatient(Transformer.MACAW);
      Patient closeMatch = transformer.makeCloseMatch(patient);
      patient.setGuardianNameFirst(patient.getMotherNameFirst());
      patient.setGuardianNameLast(patient.getMotherNameLast());
      closeMatch.setGuardianNameFirst(closeMatch.getMotherNameFirst());
      closeMatch.setGuardianNameLast(closeMatch.getMotherNameLast());
      Typest typest = new Typest(transformer);
      patientA = typest.type(patient, closeMatch, typeA, conditionA);
      patientB = typest.type(patient, closeMatch, typeB, conditionB);
      patientC = typest.type(closeMatch, patient, typeC, conditionC);
      out.println("    <form action=\"RandomServlet\"> ");
      out.println("    <input type=\"hidden\" name=\"showScores\" value=\"" + showScores + "\"/>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      Set<String> fieldNameSet = new HashSet<String>();
      fieldNameSet.addAll(patientA.getValueMap().keySet());
      fieldNameSet.addAll(patientB.getValueMap().keySet());
      fieldNameSet.addAll(patientC.getValueMap().keySet());
      List<String> fieldNameList = new ArrayList<String>(fieldNameSet);
      Collections.sort(fieldNameList);

      String expectedResultB = "Match";
      if (typeA == Typest.Type.BAD || typeB == Typest.Type.BAD) {
        expectedResultB = "Not a Match";
      } else if (typeA == Typest.Type.POOR && typeB == Typest.Type.POOR) {
        expectedResultB = "Not a Match";
      } else if (typeA == Typest.Type.POOR || typeB == Typest.Type.POOR) {
        expectedResultB = "Possible Match";
      } else if ((typeA == Typest.Type.GOODA || typeA == Typest.Type.GOODB)
          && (typeB == Typest.Type.GOODA || typeB == Typest.Type.GOODB)) {
        expectedResultB = "Possible Match";
      }
      String expectedResultC = "Not a Match";

      out.println("      <tr>");
      out.println("        <th>Field</th>");
      out.println("        <th>Patient</th>");
      out.println("        <th>Same</th>");
      out.println("        <th>Different</th>");
      out.println("      </tr>");
      out.println("      <tr>");
      out.println("        <td>Type</td>");
      out.println("        <td>");
      out.println("          <select name=\"typeA\">");
      for (int i = 0; i < Typest.Type.values().length; i++) {
        Typest.Type type = Typest.Type.values()[i];
        if (type == typeA) {
          out.println("            <option value=\"" + type + "\" selected=\"true\">" + type + "</option>");
        } else {
          out.println("            <option value=\"" + type + "\">" + type + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("        <td>");
      out.println("          <select name=\"typeB\">");
      for (int i = 0; i < Typest.Type.values().length; i++) {
        Typest.Type type = Typest.Type.values()[i];
        if (type == typeB) {
          out.println("            <option value=\"" + type + "\" selected=\"true\">" + type + "</option>");
        } else {
          out.println("            <option value=\"" + type + "\">" + type + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("        <td>");
      out.println("          <select name=\"typeC\">");
      for (int i = 0; i < Typest.Type.values().length; i++) {
        Typest.Type type = Typest.Type.values()[i];
        if (type == typeC) {
          out.println("            <option value=\"" + type + "\" selected=\"true\">" + type + "</option>");
        } else {
          out.println("            <option value=\"" + type + "\">" + type + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("      </tr>");
      out.println("      <tr>");
      out.println("        <td>Condition</td>");
      out.println("        <td>");
      out.println("          <select name=\"conditionA\">");
      out.println("            <option value=\"\">--select--</option>");
      for (int i = 0; i < Typest.Condition.values().length; i++) {
        Typest.Condition condition = Typest.Condition.values()[i];
        if (conditionA != null && condition == conditionA) {
          out.println("            <option value=\"" + condition + "\" selected=\"true\">" + condition + "</option>");
        } else {
          out.println("            <option value=\"" + condition + "\">" + condition + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("        <td>");
      out.println("          <select name=\"conditionB\">");
      out.println("            <option value=\"\">--select--</option>");
      for (int i = 0; i < Typest.Condition.values().length; i++) {
        Typest.Condition condition = Typest.Condition.values()[i];
        if (conditionB != null && condition == conditionB) {
          out.println("            <option value=\"" + condition + "\" selected=\"true\">" + condition + "</option>");
        } else {
          out.println("            <option value=\"" + condition + "\">" + condition + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("        <td>");
      out.println("          <select name=\"conditionC\">");
      out.println("            <option value=\"\">--select--</option>");
      for (int i = 0; i < Typest.Condition.values().length; i++) {
        Typest.Condition condition = Typest.Condition.values()[i];
        if (conditionC != null && condition == conditionC) {
          out.println("            <option value=\"" + condition + "\" selected=\"true\">" + condition + "</option>");
        } else {
          out.println("            <option value=\"" + condition + "\">" + condition + "</option>");
        }
      }
      out.println("          </select>");
      out.println("        </td>");
      out.println("      </tr>");
      {

        if (showScores) {
          PatientCompare patientCompareB = new PatientCompare();
          patientCompareB.setPatientA(new Patient(patientA.getValues()));
          patientCompareB.setPatientB(new Patient(patientB.getValues()));
          MatchNode matchB = patientCompareB.getMatch();
          MatchNode notMatchB = patientCompareB.getNotMatch();
          MatchNode twinB = patientCompareB.getTwin();
          MatchNode missingB = patientCompareB.getMissing();

          PatientCompare patientCompareC = new PatientCompare();
          patientCompareC.setPatientA(new Patient(patientA.getValues()));
          patientCompareC.setPatientB(new Patient(patientC.getValues()));
          MatchNode matchC = patientCompareC.getMatch();
          MatchNode notMatchC = patientCompareC.getNotMatch();
          MatchNode twinC = patientCompareC.getTwin();
          MatchNode missingC = patientCompareC.getMissing();
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Match</td>");
          out.println("        " + printScore(matchB.weightScore(patientCompareB)) + "");
          out.println("        " + printScore(matchC.weightScore(patientCompareC)) + "");
          out.println("        </tr>");
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Not a Match</td>");
          out.println("        " + printScore(notMatchB.weightScore(patientCompareB)) + "");
          out.println("        " + printScore(notMatchC.weightScore(patientCompareC)) + "");
          out.println("        </tr>");
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Suspect Twin</td>");
          out.println("        " + printScore(twinB.weightScore(patientCompareB)) + "");
          out.println("        " + printScore(twinC.weightScore(patientCompareC)) + "");
          out.println("        </tr>");
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Missing Data</td>");
          out.println("        " + printScore(missingB.weightScore(patientCompareB)) + "");
          out.println("        " + printScore(missingC.weightScore(patientCompareC)) + "");
          out.println("        </tr>");
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Result</td>");
          if (patientCompareB.getResult().equals("Match")) {
            out.println("        <td class=\"pass\">" + patientCompareB.getResult() + "</td>");
          } else {
            out.println("        <td class=\"fail\">" + patientCompareB.getResult() + "</td>");
          }
          if (patientCompareC.getResult().equals("Match")) {
            out.println("        <td class=\"pass\">" + patientCompareC.getResult() + "</td>");
          } else {
            out.println("        <td class=\"fail\">" + patientCompareC.getResult() + "</td>");
          }
          out.println("      </tr>");
          out.println("      <tr>");
          out.println("        <td>&nbsp;</td>");
          out.println("        <td align=\"right\">Expected</td>");
          if (expectedResultB.equals("Match")) {
            out.println("        <td class=\"pass\">" + expectedResultB + "</td>");
          } else {
            out.println("        <td class=\"fail\">" + expectedResultB + "</td>");
          }
          if (expectedResultC.equals("Match")) {
            out.println("        <td class=\"pass\">" + expectedResultC + "</td>");
          } else {
            out.println("        <td class=\"fail\">" + expectedResultC + "</td>");
          }
          out.println("      </tr>");
        }
      }

      for (String fieldName : displayFields) {
        out.println("      <tr>");
        String display = displayFieldLabels.get(fieldName);
        if (display == null) {
          fieldName = display;
        }

        out.println("        <td>" + display + "</td>");
        out.println("        <td>" + n(patientA.getValue(fieldName)) + "</td>");
        out.println("        <td>" + n(patientB.getValue(fieldName)) + "</td>");
        out.println("        <td>" + n(patientC.getValue(fieldName)) + "</td>");
        out.println("      </tr>");
      }
      out.println("      <tr>");
      out.println("        <td>&nbsp;</td>");
      out.println("        <td>&nbsp;</td>");
      String link = "MatchPatientServlet?patientAValues=" + URLEncoder.encode(patientA.getValues(), "UTF-8")
          + "&patientBValues=" + URLEncoder.encode(patientB.getValues(), "UTF-8");
      out.println("        <td><a href=\"" + link + "\">Compare</td></a>");
      link = "MatchPatientServlet?patientAValues=" + URLEncoder.encode(patientA.getValues(), "UTF-8")
          + "&patientBValues=" + URLEncoder.encode(patientC.getValues(), "UTF-8");
      out.println("        <td><a href=\"" + link + "\">Compare</td></a>");
      out.println("      </tr>");
      out.println("      <tr>");
      out.println("        <td colspan=\"4\" align=\"right\"><input type=\"submit\" name=\"submit\" value=\"Update\"></td>");
      out.println("      </tr>");
      out.println("    </table>");
      out.println("    </form>");

      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    }
    out.close();

  }

  private static String n(String s) {
    if (s == null || s.trim().equals("")) {
      return "&nbsp;";
    }
    return s;

  }

  private static String printScore(double d) {
    DecimalFormat df = new DecimalFormat("0.00");
    if (d > 0.5) {
      return "<td class=\"pass\" valign=\"top\">" + df.format(d) + "</td>";
    }
    return "<td class=\"fail\" valign=\"top\">" + df.format(d) + "</td>";
  }

}

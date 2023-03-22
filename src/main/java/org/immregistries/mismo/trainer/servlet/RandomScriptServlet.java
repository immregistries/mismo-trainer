package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.random.Transformer;
import org.immregistries.mismo.trainer.random.Typest;

/**
 * Creates a list of patient records, some that match and some that don't, and
 * places them in a script with a suggested match criteria. This allows for
 * rapid creation of test data.
 * 
 * @author Nathan Bunker
 * 
 */
public class RandomScriptServlet extends HomeServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

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
      out.println(
          "        if (vis.display == '' && elem.offsetWidth != undefined && elem.offsetHeight != undefined) ");
      out.println(
          "          vis.display = (elem.offsetWidth != 0 && elem.offsetHeight != 0) ? 'block' : 'none';");
      out.println(
          "        vis.display = (vis.display == '' || vis.display == 'block') ? 'none' : 'block';");
      out.println("      }");
      out.println("    </script>");
      out.println("    <h1>Random Patient Scripts</h1>");

      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr>");
      out.println("        <th>Count</th>");
      out.println("        <th>Condition</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Same Expected</th>");
      out.println("        <th>Same Actual</th>");
      out.println("        <th>Different Expected</th>");
      out.println("        <th>Different Actual</th>");
      out.println("      </tr>");
      out.flush();

      StringWriter stringWriter = new StringWriter();
      PrintWriter scriptOut = new PrintWriter(stringWriter);
      int count = 0;
      for (Typest.Condition condition : Typest.Condition.values()) {
        if (condition == Typest.Condition.BIRTH_MULTIPLE_MISSING
            || condition == Typest.Condition.BIRTH_ORDER_MISSING
            || condition == Typest.Condition.SSN_MISSING || condition == Typest.Condition.SSN_SHARED
            || condition == Typest.Condition.SSN_TYPO
            || condition == Typest.Condition.MEDICAID_NUM_MISSING
            || condition == Typest.Condition.MEDICAID_NUM_SHARED
            || condition == Typest.Condition.MEDICAID_NUM_TYPO
            || condition == Typest.Condition.SHOT_HISTORY_INCOMPLETE
            || condition == Typest.Condition.SHOT_HISTORY_MISSING) {
          continue;
        }
        for (Typest.Type typeSelected1 : Typest.Type.values()) {
          for (Typest.Type typeSelected2 : Typest.Type.values()) {
            count++;

            Typest.Type typeA = typeSelected1;
            Typest.Type typeB = typeSelected2;
            Typest.Type typeC = typeSelected2;
            Typest.Condition conditionA = condition;
            Typest.Condition conditionB = null;
            Typest.Condition conditionC = null;
            Transformer transformer = new Transformer();
            Patient patientA = null;
            Patient patientB = null;
            Patient patientC = null;
            Patient patient = transformer.createPatient(Transformer.MACAW);
            if (condition == Typest.Condition.ALIAS_MISSING) {
              transformer.addAlias(patient);
            } else if (condition == Typest.Condition.SUFFIX_MISSING) {
              transformer.addSuffix(patient);
            } else if (condition == Typest.Condition.BIRTH_MULITPLE_MISSING_FOR_TWIN
                || condition == Typest.Condition.BIRTH_ORDER_MISSING_FOR_TWIN) {
              // Force Twin
              patient.setBirthOrder("2");
              patient.setBirthStatus("Y");
              patient.setBirthType("2");
            }
            Patient closeMatch = null;
            if (!patient.getBirthOrder().equals("") && !patient.getBirthOrder().equals("1")) {
              closeMatch = transformer.makeTwin(patient);
            } else {
              closeMatch = transformer.makeCloseMatch(patient);
            }
            patient.setGuardianNameFirst(patient.getMotherNameFirst());
            patient.setGuardianNameLast(patient.getMotherNameLast());
            closeMatch.setGuardianNameFirst(closeMatch.getMotherNameFirst());
            closeMatch.setGuardianNameLast(closeMatch.getMotherNameLast());
            Typest typest = new Typest(transformer);
            patientA = typest.type(patient, closeMatch, typeA, conditionA);
            patientB = typest.type(patient, closeMatch, typeB, conditionB);
            transformer.changeMrn(patientB);
            patientC = typest.type(closeMatch, patient, typeC, conditionC);

            String expectedResultB = "Match";

            int score = 0;
            score += typeA == Typest.Type.IDEAL ? 10 : 0;
            score += typeA == Typest.Type.GREATA ? 9 : 0;
            score += typeA == Typest.Type.GREATB ? 9 : 0;
            score += typeA == Typest.Type.GOODA ? 7 : 0;
            score += typeA == Typest.Type.GOODB ? 7 : 0;
            score += typeA == Typest.Type.POOR ? 2 : 0;
            score += typeB == Typest.Type.IDEAL ? 10 : 0;
            score += typeB == Typest.Type.GREATA ? 9 : 0;
            score += typeB == Typest.Type.GREATB ? 9 : 0;
            score += typeB == Typest.Type.GOODA ? 7 : 0;
            score += typeB == Typest.Type.GOODB ? 7 : 0;
            score += typeB == Typest.Type.POOR ? 2 : 0;

            // Assume low
            int highScore = 14;
            int lowScore = 8;
            // Medium first
            if (condition == Typest.Condition.ADDRESS_CHANGED
                || condition == Typest.Condition.ADDRESS_TYPO
                || condition == Typest.Condition.ADDRESS_STREET_MISSING
                || condition == Typest.Condition.FIRST_NAME_CHANGED
                || condition == Typest.Condition.PHONE_CHANGED
                || condition == Typest.Condition.SSN_TYPO) {
              highScore = 15;
              lowScore = 10;
            }
            // High
            if (condition == Typest.Condition.DOB_VALUE_SWAPPED
                || condition == Typest.Condition.DOB_OFF_BY_1
                || condition == Typest.Condition.MEDICAID_NUM_SHARED
                || condition == Typest.Condition.MOTHERS_MAIDEN_NAME_CHANGED
                || condition == Typest.Condition.MRN_SHARED_MRN
                || condition == Typest.Condition.SSN_SHARED
                || condition == Typest.Condition.BIRTH_MULITPLE_MISSING_FOR_TWIN
                || condition == Typest.Condition.BIRTH_ORDER_MISSING_FOR_TWIN) {
              highScore = 18;
              lowScore = 10;
            }
            if (score >= highScore) {
              expectedResultB = "Match";
            } else if (score <= lowScore) {
              expectedResultB = "Possible Match";
            } else {
              expectedResultB = "Not Sure";
            }

            String expectedResultC = "Not a Match";

            if (!patient.getBirthOrder().equals("") && !patient.getBirthOrder().equals("1")) {
              expectedResultB = "Not Sure";
              expectedResultC = "Not Sure";
            }

            PatientCompare patientCompareB = new PatientCompare();
            patientCompareB.setPatientA(new Patient(patientA.getValues()));
            patientCompareB.setPatientB(new Patient(patientB.getValues()));

            PatientCompare patientCompareC = new PatientCompare();
            patientCompareC.setPatientA(new Patient(patientA.getValues()));
            patientCompareC.setPatientB(new Patient(patientC.getValues()));

            out.println("      <tr>");
            out.println("        <td>" + count + "</td>");
            out.println("        <td>" + condition + "</td>");
            out.println("        <td>" + typeSelected1 + "</td>");
            String resultB = patientCompareB.getResult();
            if (resultB.equals(expectedResultB)) {
              out.println("        <td class=\"pass\">" + expectedResultB + "</td>");
              out.println("        <td class=\"pass\">" + resultB + "</td>");
            } else {
              out.println("        <td class=\"fail\">" + expectedResultB + "</td>");
              out.println("        <td class=\"fail\">" + resultB + "</td>");
            }
            String resultC = patientCompareC.getResult();
            if (resultB.equals(expectedResultB)) {
              out.println("        <td class=\"pass\">" + expectedResultC + "</td>");
              out.println("        <td class=\"pass\">" + resultC + "</td>");
            } else {
              out.println("        <td class=\"fail\">" + expectedResultC + "</td>");
              out.println("        <td class=\"fail\">" + resultC + "</td>");
            }
            out.println("      </tr>");
            out.flush();
            scriptOut.println(
                "TEST: S-" + count + ":" + condition + ":" + typeSelected1 + "-" + typeSelected2);
            scriptOut.println("EXPECT: " + expectedResultB);
            scriptOut.println("PATIENT A: " + patientA.getValues());
            scriptOut.println("PATIENT B: " + patientB.getValues());
            scriptOut.println(
                "TEST: D-" + count + ":" + condition + ":" + typeSelected1 + "-" + typeSelected2);
            scriptOut.println("EXPECT: " + expectedResultC);
            scriptOut.println("PATIENT A: " + patientA.getValues());
            scriptOut.println("PATIENT B: " + patientC.getValues());
          }
        }
      }
      scriptOut.close();
      out.println("    </table>");

      out.println("    <br>");
      out.println("    <pre>");
      out.print(stringWriter.toString());
      out.println("    </pre>");
      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</Fpre>");
    }
    out.close();

  }

}

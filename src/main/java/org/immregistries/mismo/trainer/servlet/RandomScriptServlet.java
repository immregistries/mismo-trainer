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
import org.immregistries.mismo.trainer.random.Typest.PatientDataQuality;

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
        for (PatientDataQuality patientDataQualitySelected1 : PatientDataQuality.values()) {
          for (PatientDataQuality patientDataQualitySelected2 : PatientDataQuality.values()) {
            if(patientDataQualitySelected1.equals(Typest.PatientDataQuality.GOODB) ||
               patientDataQualitySelected2.equals(Typest.PatientDataQuality.GOODB)){
                continue;
            }
            count++;

            PatientDataQuality patientDataQualityA = patientDataQualitySelected1;
            PatientDataQuality patientDataQualityB = patientDataQualitySelected2;
            PatientDataQuality patientDataQualityC = patientDataQualitySelected2;
            Typest.Condition conditionA = condition;
            Typest.Condition conditionB = null;
            Typest.Condition conditionC = null;
            Transformer transformer = new Transformer();
            Patient patientA = null;
            Patient patientB = null;
            Patient patientC = null;
            Patient patient = transformer.createPatient(Transformer.MACAW);
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
            patientA = typest.makePatientVariation(patient, closeMatch, patientDataQualityA, conditionA);
            patientB = typest.makePatientVariation(patient, closeMatch, patientDataQualityB, conditionB);
            transformer.changeMrn(patientB);
            patientC = typest.makePatientVariation(closeMatch, patient, patientDataQualityC, conditionC);

            String expectedResultB = "Match";

            int score = 0;
            score += patientDataQualityA == PatientDataQuality.IDEAL ? 10 : 0;
            score += patientDataQualityA == PatientDataQuality.GOODB ? 7 : 0;
            score += patientDataQualityA == PatientDataQuality.POOR ? 2 : 0;
            score += patientDataQualityB == PatientDataQuality.IDEAL ? 10 : 0;
            score += patientDataQualityB == PatientDataQuality.GOODB ? 7 : 0;
            score += patientDataQualityB == PatientDataQuality.POOR ? 2 : 0;

            // Assume low
            int highScore = 14;
            int lowScore = 8;
            // Medium first
            if (condition == Typest.Condition.ADDRESS_CHANGED
                || condition == Typest.Condition.ADDRESS_TYPO
                || condition == Typest.Condition.ADDRESS_STREET_MISSING
                || condition == Typest.Condition.FIRST_NAME_CHANGED
                || condition == Typest.Condition.PHONE_CHANGED) {
              highScore = 15;
              lowScore = 10;
            }
            // High
            if (condition == Typest.Condition.DOB_VALUE_SWAPPED
                || condition == Typest.Condition.DOB_OFF_BY_1) {
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
            out.println("        <td>" + patientDataQualitySelected1 + "</td>");
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
                "TEST: S-" + count + ":" + condition + ":" + patientDataQualitySelected1
                    + "-" + patientDataQualitySelected2);
            scriptOut.println("EXPECT: " + expectedResultB);
            scriptOut.println("PATIENT A: " + patientA.getValues());
            scriptOut.println("PATIENT B: " + patientB.getValues());
            scriptOut.println(
                "TEST: D-" + count + ":" + condition + ":" + patientDataQualitySelected1
                    + "-" + patientDataQualitySelected2);
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

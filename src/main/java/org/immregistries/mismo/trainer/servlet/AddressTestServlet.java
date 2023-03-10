package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.model.Address;
import org.immregistries.mismo.match.model.User;


/**
 * Creates a list of patient records, some that match and some that don't, and
 * places them in a script with a suggested match criteria. This allows for
 * rapid creation of test data.
 * 
 * @author Nathan Bunker
 * 
 */
public class AddressTestServlet extends HomeServlet
{

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }


  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    response.setContentType("text/html");
    PrintWriter out = new PrintWriter(response.getOutputStream());
    HttpSession session = request.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Address Test</h1>");

      List<Address> addressList = new ArrayList<Address>();
      String data = request.getParameter("data");
      if (data != null) {
        StringReader stringReader = new StringReader(data);
        BufferedReader in = new BufferedReader(stringReader);
        String line;
        while ((line = in.readLine()) != null) {
          String parts[] = line.split("\\t");
          Address address = new Address();
          address.setLine1(readPart(0, parts));
          address.setLine2(readPart(1, parts));
          address.setCity(readPart(2, parts));
          address.setState(readPart(3, parts));
          address.setZip(readPart(4, parts));
          address.parseAddress();
          addressList.add(address);
        }
        in.close();
      }
      if (addressList.size() > 0) {
        out.println("  <table border=\"1\" cellspacing=\"0\">");
        out.println("    <tr>");
        out.println("      <th>Type</th>");
        out.println("      <th>Line 1</th>");
        out.println("      <th>Line 2</th>");
        out.println("      <th>Number</th>");
        out.println("      <th>Street Name</th>");
        out.println("      <th>Apartment</th>");
        out.println("      <th>City</th>");
        out.println("      <th>State</th>");
        out.println("      <th>Zip</th>");
        out.println("      <th>Zip 5 Only</th>");
        out.println("    </tr>");
        for (Address address : addressList) {
          out.println("    <tr>");
          out.println("      <td>" + address.getAddressType() + "</td>");
          out.println("      <td>" + address.getLine1() + "</td>");
          out.println("      <td>" + address.getLine2() + "</td>");
          out.println("      <td>" + address.getNumber() + "</td>");
          out.println("      <td>" + address.getStreetName() + "</td>");
          out.println("      <td>" + address.getApartment() + "</td>");
          out.println("      <td>" + address.getCity() + "</td>");
          out.println("      <td>" + address.getState() + "</td>");
          out.println("      <td>" + address.getZip() + "</td>");
          out.println("      <td>" + address.getZip5Only() + "</td>");
        }
        out.println("  </table><br/>");
      }

      out.println("    <form action=\"AddressTestServlet\" method=\"POST\">");
      out.println("    Address Data from Excel:<br/>");
      out.println("   <textarea name=\"data\" cols=\"70\" rows=\"5\" wrap=\"off\"></textarea><br/>");
      out.println("   <input type=\"submit\" name=\"submit\" value=\"Test\"/>");
      out.println("    </form>");

      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    }
    out.close();

  }


  private static String readPart(int pos, String[] parts) {
    if (parts != null && parts.length > pos) {
      if (parts[pos] != null) {
        return parts[pos].trim();
      }
    }
    return "";
  }



}

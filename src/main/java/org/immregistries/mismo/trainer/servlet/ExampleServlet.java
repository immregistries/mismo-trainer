package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.immregistries.mismo.match.model.User;

import com.wcohen.ss.JaroWinkler;

public class ExampleServlet extends HomeServlet {
  

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO Auto-generated method stub
        super.doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }

      HomeServlet.doHeader(out, user, null);

      String a = req.getParameter("AName");
      String b = req.getParameter("BName");
      if (a == null) {
        a = "";
      }
      if (b == null) {
        b = "";
      }
      
      JaroWinkler jw = new JaroWinkler();
      double result = -1.0;
      if (!a.equals("")) {
        if (!b.equals("")) {
          result = jw.score(a, b);
        }
      }

      
      out.println(" <h2> JaroWinkler Comparator</h2>");
      out.println("    <form action=\"ExampleServlet\" method=\"GET\">");
      out.println("        <input type=\"hidden\" name=\"action\" value=\"requestStartScript\"/>");
      out.println("        <label for=\"A\">A:</label>");
      out.println("        <input type=\"text\" name=\"AName\" id=\"A\" value=\"" + a + "\" />");
      out.println("        <label for=\"B\">B:</label>");
      out.println("        <input type=\"text\" name=\"BName\" id=\"B\" value=\"" + b + "\" />");
      out.println("        <input type=\"submit\" name=\"submit\" value=\"Submit\"/>");
      out.println("    </form>");
      
      
      out.println(" <p>Similarity: " + result + "</p>");
      
      HomeServlet.doFooter(out, req);
    }
    finally {
      out.close();
      teardown(req, resp);
    }
    }
}

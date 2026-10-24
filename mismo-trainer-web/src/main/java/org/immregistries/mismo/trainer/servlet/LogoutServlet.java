package org.immregistries.mismo.trainer.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.immregistries.interophub.client.InteropHubClient;
import org.immregistries.mismo.trainer.auth.HubClientSupport;

public class LogoutServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    logout(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    logout(req, resp);
  }

  private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession session = req.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    InteropHubClient client = HubClientSupport.getInteropHubClient();
    resp.sendRedirect(client == null ? req.getContextPath() + "/HomeServlet" : client.getHubHomeUrl());
  }
}

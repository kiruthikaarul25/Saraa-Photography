package com.shop.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class OwnerNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.owner-email}")
    private String ownerEmail;

    @Value("${app.mail.from}")
    private String senderEmail;

    @Value("${app.base-url:http://localhost:8765}")
    private String baseUrl;

    public OwnerNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendApprovalRequest(
            String type, String username, String rawToken) {

        String requestType = "SIGNUP".equals(type)
                ? "New Admin Signup"
                : "Admin Password Change";

        String base = baseUrl.replaceAll("/$", "")
                + "/owner/approval/" + rawToken;

        String acceptUrl = base + "?action=accept";
        String rejectUrl = base + "?action=reject";

        String html =
            "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">"
          + "<p>Hello Owner,</p>"
          + "<p>A new request requires your approval.</p>"
          + "<p><b>Request Type:</b> " + HtmlUtils.htmlEscape(requestType) + "<br>"
          + "<b>Admin Username:</b> " + HtmlUtils.htmlEscape(username) + "</p>"
          + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:20px 0;\"><tr>"
          + "<td style=\"padding-right:12px;\">"
          + "<a href=\"" + acceptUrl + "\" style=\"background:#2e7d32;color:#ffffff;"
          + "padding:12px 28px;text-decoration:none;border-radius:6px;font-weight:bold;"
          + "display:inline-block;\">Accept</a></td>"
          + "<td><a href=\"" + rejectUrl + "\" style=\"background:#c62828;color:#ffffff;"
          + "padding:12px 28px;text-decoration:none;border-radius:6px;font-weight:bold;"
          + "display:inline-block;\">Reject</a></td>"
          + "</tr></table>"
          + "<p style=\"color:#666;font-size:12px;\">"
          + "No password is included in this email.<br>"
          + "Each button opens a confirmation page; the decision is saved only when you press the confirm button.<br>"
          + "Links expire in 30 minutes and can be used only once.</p>"
          + "<p>Saara Photography</p>"
          + "</div>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(ownerEmail);
            helper.setSubject("Saara Photography - Approval Required: " + requestType);
            helper.setText(html, true);   // true = HTML

            mailSender.send(message);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not send approval email.", ex);
        }
    }
}
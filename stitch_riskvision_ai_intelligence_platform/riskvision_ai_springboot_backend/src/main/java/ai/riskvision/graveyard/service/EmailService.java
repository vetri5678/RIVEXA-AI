package ai.riskvision.graveyard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String otpCode) {
        String resetLink = frontendUrl + "/#/password-reset?otp=" + otpCode;
        String htmlContent = buildEmailBody(
            "Password Reset Verification Code",
            "Your Password Reset OTP",
            "We received a request to reset your RiskVision AI account password. Enter the 6-digit verification code below on the password reset page.",
            "Account: " + toEmail + "<br/><strong>Verification Code (OTP): <span style=\"font-size: 24px; color: #00d4ff; letter-spacing: 4px;\">" + otpCode + "</span></strong><br/>Expiration: 15 minutes",
            "RESET PASSWORD",
            resetLink,
            "Security Warning: If you did not request this, you can safely ignore this email. Your password remains unchanged."
        );
        sendHtmlEmail(toEmail, "RiskVision AI - Password Reset Code: " + otpCode, htmlContent);
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationLink = frontendUrl + "/#/verify-email?token=" + token;
        String htmlContent = buildEmailBody(
            "Verify Your Email",
            "Security Verification Node",
            "Thank you for registering on the RiskVision AI platform. Please verify your email address to establish a secure data link.",
            "Target Verification Profile: " + toEmail + "<br/>Process Status: Pending Activation.",
            "VERIFY SECURE LINK",
            verificationLink,
            "If you did not register for this account, please ignore this email or contact security administrator."
        );
        sendHtmlEmail(toEmail, "RiskVision AI - Verify Your Email", htmlContent);
    }

    public void sendWelcomeEmail(String toEmail, String username) {
        String dashboardLink = frontendUrl + "/#/login";
        String htmlContent = buildEmailBody(
            "Welcome to RiskVision AI",
            "Welcome, " + username + "!",
            "Your profile has been successfully configured on the RiskVision AI Predictive Project Intelligence Platform.",
            "Assigned Handle: " + username + "<br/>Default Role: VIEWER<br/>Status: Fully Operational.",
            "ACCESS COMMAND CENTER",
            dashboardLink,
            "Start syncing repository nodes and review real-time failure hazard prediction metrics."
        );
        sendHtmlEmail(toEmail, "Welcome to RiskVision AI!", htmlContent);
    }

    /**
     * Sends a login notification email to the user after every successful OAuth sign-in.
     *
     * <p>The email contains login details (time, provider, browser, OS, IP) and a
     * "Go to Dashboard" button that links to the plain dashboard route — the JWT is
     * never embedded in the URL.  If the user's session is expired when they click,
     * the frontend PrivateRoute guard redirects them to /login automatically.
     *
     * <p>This method is annotated {@code @Async} so that an SMTP failure never blocks
     * the OAuth redirect flow.
     *
     * @param toEmail           recipient email address
     * @param displayName       user's full name or username
     * @param loginTime         timestamp of the login event
     * @param provider          OAuth provider name ("Google OAuth", "GitHub OAuth", etc.)
     * @param browser           parsed browser label from User-Agent
     * @param operatingSystem   parsed OS label from User-Agent
     * @param ipAddress         client IP address from request
     */
    @Async("emailTaskExecutor")
    public void sendLoginNotificationEmail(
            String toEmail,
            String displayName,
            LocalDateTime loginTime,
            String provider,
            String browser,
            String operatingSystem,
            String ipAddress) {

        String dashboardUrl = frontendUrl + "/#/dashboard";

        // Format login time for human-readable display
        String formattedTime = loginTime != null
                ? loginTime.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy  HH:mm:ss")) + " UTC"
                : "Unknown";

        // Build the details table rows
        String detailsHtml =
                "<table style=\"width:100%; border-collapse:collapse; font-family:monospace; font-size:13px; color:#e2e8f0;\">"
                + buildDetailRow("Account",           escapeHtml(toEmail))
                + buildDetailRow("Login Time",        escapeHtml(formattedTime))
                + buildDetailRow("Provider",          escapeHtml(provider != null ? provider : "OAuth"))
                + buildDetailRow("Browser",           escapeHtml(browser != null ? browser : "Unknown"))
                + buildDetailRow("Operating System",  escapeHtml(operatingSystem != null ? operatingSystem : "Unknown"))
                + buildDetailRow("IP Address",        escapeHtml(ipAddress != null ? ipAddress : "Unknown"))
                + "</table>";

        String securityNotice =
                "<div style=\"margin-top:24px; padding:16px; background-color:#1c1f3a; border-left:4px solid #f59e0b; border-radius:6px;\">"
                + "<p style=\"color:#fbbf24; font-size:13px; font-weight:bold; margin:0 0 8px 0;\">\u26a0 Not you?</p>"
                + "<p style=\"color:#94a3b8; font-size:12px; margin:0; line-height:1.6;\">If you did not perform this login, "
                + "<strong>immediately reset your password</strong> and contact the RiskVision platform administrator. "
                + "Your account may have been accessed without authorization.</p>"
                + "</div>";

        String mainContent = detailsHtml + securityNotice;

        String htmlContent = buildEmailBody(
                "Login Notification — RiskVision AI",
                "Successful Sign-In Detected",
                "Hello " + escapeHtml(displayName != null ? displayName : toEmail) + ","
                + " a successful sign-in to your <strong style=\"color:#00d4ff;\">RiskVision AI</strong> account has been detected.",
                mainContent,
                "GO TO DASHBOARD",
                dashboardUrl,
                "This is an automated security notification. Do not reply to this email. "
                + "If you have questions, contact your platform administrator."
        );

        sendHtmlEmail(toEmail, "RiskVision AI \u2014 Successful Login Notification", htmlContent);
    }

    /** Builds a single 2-column detail row for the login info table. */
    private String buildDetailRow(String label, String value) {
        return "<tr>"
                + "<td style=\"padding:8px 12px 8px 0; color:#64748b; white-space:nowrap; vertical-align:top;\">" + label + "</td>"
                + "<td style=\"padding:8px 0; color:#e2e8f0; word-break:break-all;\">" + value + "</td>"
                + "</tr>";
    }

    /** Minimal HTML escaping to prevent injection in dynamically built email content. */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    public void sendNotificationEmail(String toEmail, String subject, String messageContent) {
        String htmlContent = buildEmailBody(
            "RiskVision Notification",
            "Operational Alert",
            "A security or operational telemetry update has been dispatched for your account.",
            "Alert Context: " + messageContent,
            null,
            null,
            "This is an automated system message. Do not reply to this transmission."
        );
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    private String buildEmailBody(String title, String heading, String intro, String mainContent, String ctaText, String ctaUrl, String footerMessage) {
        String ctaButtonHtml = "";
        if (ctaText != null && ctaUrl != null) {
            ctaButtonHtml = "<div style=\"text-align: center; margin: 30px 0;\">"
                + "<!--[if mso]>"
                + "<v:roundrect xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" href=\"" + ctaUrl + "\" style=\"height:45px;v-text-anchor:middle;width:220px;\" arcsize=\"10%\" stroke=\"f\" fillcolor=\"#00d4ff\">"
                + "<w:anchorlock/>"
                + "<center style=\"color:#0a0e17;font-family:sans-serif;font-size:14px;font-weight:bold;\">" + ctaText + "</center>"
                + "</v:roundrect>"
                + "<![endif]-->"
                + "<a href=\"" + ctaUrl + "\" style=\"background-color: #00d4ff; color: #0a0e17; display: inline-block; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 13px; font-weight: bold; line-height: 45px; text-align: center; text-decoration: none; width: 220px; -webkit-text-size-adjust: none; mso-hide: all; border-radius: 6px; box-shadow: 0 4px 6px rgba(0, 212, 255, 0.2); text-transform: uppercase; letter-spacing: 1px;\">" + ctaText + "</a>"
                + "</div>";
        }

        return "<!DOCTYPE html>"
            + "<html>"
            + "<head>"
            + "  <meta charset=\"utf-8\">"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "  <title>" + title + "</title>"
            + "  <style>"
            + "    @media only screen and (max-width: 600px) {"
            + "      .container { width: 100% !important; padding: 15px !important; }"
            + "    }"
            + "  </style>"
            + "</head>"
            + "<body style=\"background-color: #0a0e17; color: #c4d1ec; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 0;\">"
            + "  <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color: #0a0e17; padding: 40px 0;\">"
            + "    <tr>"
            + "      <td align=\"center\">"
            + "        <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"600\" class=\"container\" style=\"background-color: #0f172a; border: 1px solid #1e293b; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.5);\">"
            + "          <!-- Header Banner -->"
            + "          <tr>"
            + "            <td align=\"center\" style=\"background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%); padding: 30px; border-bottom: 2px solid #00d4ff;\">"
            + "              <h1 style=\"color: #00d4ff; font-size: 24px; font-weight: 900; letter-spacing: 2px; margin: 0; text-transform: uppercase;\">RISKVISION AI</h1>"
            + "              <p style=\"color: #94a3b8; font-size: 10px; font-weight: bold; letter-spacing: 3px; margin: 5px 0 0 0; text-transform: uppercase;\">Predictive Project Intelligence</p>"
            + "            </td>"
            + "          </tr>"
            + "          <!-- Content Body -->"
            + "          <tr>"
            + "            <td style=\"padding: 40px 30px;\">"
            + "              <h2 style=\"color: #f1f5f9; font-size: 18px; font-weight: bold; margin-top: 0; margin-bottom: 20px;\">" + heading + "</h2>"
            + "              <p style=\"color: #94a3b8; font-size: 14px; line-height: 1.6; margin-bottom: 20px;\">" + intro + "</p>"
            + "              <div style=\"background-color: #020617; border-left: 4px solid #00d4ff; border-radius: 6px; padding: 20px; margin-bottom: 25px; color: #e2e8f0; font-family: monospace; font-size: 13px; line-height: 1.5;\">"
            + "                " + mainContent
            + "              </div>"
            + "              " + ctaButtonHtml
            + "              <p style=\"color: #64748b; font-size: 12px; line-height: 1.5; margin-top: 30px; border-top: 1px solid #1e293b; padding-top: 20px;\">"
            + "                " + footerMessage
            + "              </p>"
            + "            </td>"
            + "          </tr>"
            + "          <!-- Footer -->"
            + "          <tr>"
            + "            <td align=\"center\" style=\"background-color: #020617; padding: 20px; text-align: center;\">"
            + "              <p style=\"color: #475569; font-size: 10px; font-weight: bold; letter-spacing: 1px; margin: 0; text-transform: uppercase;\">"
            + "                © 2026 STITCH RISKVISION. ALL RIGHTS RESERVED."
            + "              </p>"
            + "            </td>"
            + "          </tr>"
            + "        </table>"
            + "      </td>"
            + "    </tr>"
            + "  </table>"
            + "</body>"
            + "</html>";
    }

    /**
     * Guard: returns false for internal/fake email domains that have no real mail server.
     * This prevents bounce messages and SMTP connection errors from fake addresses like
     * admin@riskvision.ai, user@example.com, test@localhost, etc.
     */
    private boolean isDeliverableEmail(String email) {
        if (email == null || !email.contains("@")) return false;
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase();
        // Block known fake/internal domains
        if (domain.equals("riskvision.ai") || domain.equals("example.com")
                || domain.equals("localhost") || domain.equals("test.com")
                || domain.equals("mailtest.com") || domain.endsWith(".local")
                || domain.equals("placeholder.com")) {
            log.warn("Skipping email to {} — domain '{}' has no real mail server. " +
                     "Update app.admin.email in application.properties to use a real address.", email, domain);
            return false;
        }
        return true;
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        // Skip sending to addresses on domains with no real mail server to avoid bounce errors
        if (!isDeliverableEmail(to)) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email successfully sent to {}", to);
        } catch (MessagingException | MailException e) {
            // Log the failure but do NOT throw — email delivery failures should never crash
            // the main request flow (login, registration, OAuth callback, etc.)
            log.error("Failed to send email to {}: {}. " +
                      "Check SMTP credentials and verify the recipient address is valid.", to, e.getMessage());
        }
    }
}

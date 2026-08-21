package ai.riskvision.graveyard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    @Value("${app.admin.notification-email:${ADMIN_NOTIFICATION_EMAIL:admin@riskvision.ai}}")
    private String adminNotificationEmail;

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
     * @param userId            internal user ID
     * @param userEmail         user email address
     * @param displayName       user's full name or username
     * @param loginTime         timestamp of the login event
     * @param provider          OAuth provider name ("Google OAuth", "GitHub OAuth", etc.)
     * @param browser           parsed browser label from User-Agent
     * @param operatingSystem   parsed OS label from User-Agent
     * @param ipAddress         client IP address from request
     */
    @Async("emailTaskExecutor")
    public void sendLoginNotificationEmail(
            String userId,
            String userEmail,
            String displayName,
            LocalDateTime loginTime,
            String provider,
            String browser,
            String operatingSystem,
            String ipAddress) {

        String dashboardUrl = frontendUrl + "/#/dashboard";
        String securityLogsUrl = frontendUrl + "/#/admin/login-activity";

        // Format login time
        String formattedTime = loginTime != null
                ? loginTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (System Time)"
                : "Unknown";

        String authMethod = "Credentials".equalsIgnoreCase(provider) ? "Credentials" : "OAuth2";

        // ── FUTURISTIC HTML TEMPLATE ───────────────────────────────────────────
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>🔐 New Login Detected — RiskVision AI</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 40px 0; background-color: #050814; font-family: 'Inter', system-ui, -apple-system, sans-serif;\">\n" +
                "  <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 600px; margin: 0 auto; background-color: #0f172a; border: 1px solid rgba(56, 189, 248, 0.15); border-radius: 16px; overflow: hidden; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.3);\">\n" +
                "    <!-- Header Banner -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 30px 40px; background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%); text-align: center; border-bottom: 1px solid rgba(56, 189, 248, 0.1);\">\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n" +
                "          <tr>\n" +
                "            <td style=\"background-color: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 12px; padding: 10px;\">\n" +
                "              <span style=\"font-size: 24px;\">🔐</span>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "        <h1 style=\"color: #ffffff; font-size: 20px; font-weight: 800; letter-spacing: 2px; margin: 15px 0 5px 0; text-transform: uppercase;\">RISKVISION AI</h1>\n" +
                "        <p style=\"color: #38bdf8; font-size: 10px; font-weight: 700; letter-spacing: 3px; margin: 0; text-transform: uppercase;\">SECURITY MONITORING SYSTEM</p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Content Body -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 40px;\">\n" +
                "        <h2 style=\"color: #ffffff; font-size: 18px; font-weight: 700; margin: 0 0 20px 0; text-align: center;\">NEW LOGIN DETECTED</h2>\n" +
                "        \n" +
                "        <p style=\"color: #94a3b8; font-size: 14px; line-height: 1.6; margin: 0 0 30px 0; text-align: center;\">\n" +
                "          A new authentication event was detected on your RiskVision AI application.\n" +
                "        </p>\n" +
                "\n" +
                "        <!-- Security Status Badge -->\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom: 30px;\">\n" +
                "          <tr>\n" +
                "            <td style=\"background-color: rgba(16, 185, 129, 0.1); border: 1px solid #10b981; border-radius: 30px; padding: 8px 24px; text-align: center;\">\n" +
                "              <span style=\"color: #10b981; font-size: 12px; font-weight: 800; letter-spacing: 1.5px; text-transform: uppercase;\">SUCCESSFUL LOGIN</span>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "\n" +
                "        <!-- User Information Card -->\n" +
                "        <div style=\"background-color: #030712; border-left: 4px solid #7c3aed; border-radius: 8px; padding: 20px; margin-bottom: 24px;\">\n" +
                "          <h3 style=\"color: #7c3aed; font-size: 12px; font-weight: 800; letter-spacing: 1px; margin: 0 0 15px 0; text-transform: uppercase;\">User Profile Info</h3>\n" +
                "          <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"font-size: 13px; color: #94a3b8; line-height: 1.8;\">\n" +
                "            <tr>\n" +
                "              <td width=\"35%\" style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">User Name:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(displayName) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">User Email:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(userEmail) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">User ID:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0; font-family: monospace;\">" + escapeHtml(userId) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Provider:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(provider) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Auth Method:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(authMethod) + "</td>\n" +
                "            </tr>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Login Details Card -->\n" +
                "        <div style=\"background-color: #030712; border-left: 4px solid #00d4ff; border-radius: 8px; padding: 20px; margin-bottom: 30px;\">\n" +
                "          <h3 style=\"color: #38bdf8; font-size: 12px; font-weight: 800; letter-spacing: 1px; margin: 0 0 15px 0; text-transform: uppercase;\">Login Telemetry</h3>\n" +
                "          <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"font-size: 13px; color: #94a3b8; line-height: 1.8;\">\n" +
                "            <tr>\n" +
                "              <td width=\"35%\" style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Date/Time:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(formattedTime) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">IP Address:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0; font-family: monospace;\">" + escapeHtml(ipAddress) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Browser:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(browser) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Operating System:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(operatingSystem) + "</td>\n" +
                "            </tr>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Action Buttons -->\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 auto 10px auto;\">\n" +
                "          <tr>\n" +
                "            <td align=\"center\" style=\"padding: 10px;\">\n" +
                "              <a href=\"" + dashboardUrl + "\" style=\"display: inline-block; background: linear-gradient(135deg, #38bdf8 0%, #0369a1 100%); color: #ffffff; font-size: 13px; font-weight: 700; text-decoration: none; padding: 12px 30px; border-radius: 8px; box-shadow: 0 4px 10px rgba(56, 189, 248, 0.3); text-transform: uppercase; letter-spacing: 0.5px;\">VIEW ADMIN DASHBOARD</a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "          <tr>\n" +
                "            <td align=\"center\" style=\"padding: 10px;\">\n" +
                "              <a href=\"" + securityLogsUrl + "\" style=\"display: inline-block; background-color: transparent; border: 1px solid rgba(56, 189, 248, 0.4); color: #38bdf8; font-size: 13px; font-weight: 700; text-decoration: none; padding: 11px 30px; border-radius: 8px; text-transform: uppercase; letter-spacing: 0.5px;\">VIEW LOGIN ACTIVITY</a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Footer Section -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 30px 40px; background-color: #020617; text-align: center; border-top: 1px solid rgba(56, 189, 248, 0.05);\">\n" +
                "        <p style=\"color: #475569; font-size: 11px; line-height: 1.5; margin: 0 0 10px 0;\">\n" +
                "          This is an automated security notification from RiskVision AI.<br>Do not reply to this automated message.\n" +
                "        </p>\n" +
                "        <p style=\"color: #334155; font-size: 9px; font-weight: 800; letter-spacing: 1.5px; margin: 0; text-transform: uppercase;\">\n" +
                "          © 2026 STITCH RISKVISION. ALL RIGHTS RESERVED.\n" +
                "        </p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";

        // ── PLAIN TEXT FALLBACK ───────────────────────────────────────────────
        String plainText = "RISKVISION AI - SECURITY MONITORING SYSTEM\n" +
                "===========================================\n" +
                "NEW LOGIN DETECTED\n" +
                "Status: SUCCESSFUL LOGIN\n\n" +
                "A new authentication event was detected on your RiskVision AI application.\n\n" +
                "USER PROFILE INFO:\n" +
                "-----------------\n" +
                "Name: " + displayName + "\n" +
                "Email: " + userEmail + "\n" +
                "ID: " + userId + "\n" +
                "Provider: " + provider + "\n" +
                "Auth Method: " + authMethod + "\n\n" +
                "LOGIN TELEMETRY:\n" +
                "---------------\n" +
                "Date/Time: " + formattedTime + "\n" +
                "IP Address: " + ipAddress + "\n" +
                "Browser: " + browser + "\n" +
                "OS: " + operatingSystem + "\n\n" +
                "ACTIONS:\n" +
                "--------\n" +
                "Admin Dashboard: " + dashboardUrl + "\n" +
                "Login Activity Logs: " + securityLogsUrl + "\n\n" +
                "This is an automated security notification from RiskVision AI. Do not reply to this message.";

        sendHtmlEmail(adminNotificationEmail, "🔐 New Login Detected — RiskVision AI", htmlContent, plainText);
    }

    @Async("emailTaskExecutor")
    public void sendFailedLoginAlertEmail(
            String userEmail,
            String displayName,
            int failedAttempts,
            String ipAddress,
            String browser,
            String operatingSystem) {

        String dashboardUrl = frontendUrl + "/#/dashboard";
        String securityLogsUrl = frontendUrl + "/#/admin/login-activity";
        String formattedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (System Time)";

        // ── FUTURISTIC HTML TEMPLATE (ALERT) ──────────────────────────────────
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>⚠️ Security Alert — Multiple Failed Login Attempts</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 40px 0; background-color: #050814; font-family: 'Inter', system-ui, -apple-system, sans-serif;\">\n" +
                "  <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 600px; margin: 0 auto; background-color: #0f172a; border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 16px; overflow: hidden; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.3);\">\n" +
                "    <!-- Header Banner -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 30px 40px; background: linear-gradient(135deg, #7f1d1d 0%, #0f172a 100%); text-align: center; border-bottom: 1px solid rgba(239, 68, 68, 0.2);\">\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n" +
                "          <tr>\n" +
                "            <td style=\"background-color: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 12px; padding: 10px;\">\n" +
                "              <span style=\"font-size: 24px;\">⚠️</span>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "        <h1 style=\"color: #ffffff; font-size: 20px; font-weight: 800; letter-spacing: 2px; margin: 15px 0 5px 0; text-transform: uppercase;\">RISKVISION AI</h1>\n" +
                "        <p style=\"color: #ef4444; font-size: 10px; font-weight: 700; letter-spacing: 3px; margin: 0; text-transform: uppercase;\">SECURITY MONITORING SYSTEM</p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Content Body -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 40px;\">\n" +
                "        <h2 style=\"color: #ffffff; font-size: 18px; font-weight: 700; margin: 0 0 20px 0; text-align: center;\">MULTIPLE FAILED LOGINS DETECTED</h2>\n" +
                "        \n" +
                "        <p style=\"color: #94a3b8; font-size: 14px; line-height: 1.6; margin: 0 0 30px 0; text-align: center;\">\n" +
                "          An account lockout has been triggered due to consecutive failed authentication attempts.\n" +
                "        </p>\n" +
                "\n" +
                "        <!-- Security Status Badge -->\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom: 30px;\">\n" +
                "          <tr>\n" +
                "            <td style=\"background-color: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; border-radius: 30px; padding: 8px 24px; text-align: center;\">\n" +
                "              <span style=\"color: #ef4444; font-size: 12px; font-weight: 800; letter-spacing: 1.5px; text-transform: uppercase;\">ACCOUNT TEMPORARILY LOCKED</span>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "\n" +
                "        <!-- User Information Card -->\n" +
                "        <div style=\"background-color: #030712; border-left: 4px solid #ef4444; border-radius: 8px; padding: 20px; margin-bottom: 24px;\">\n" +
                "          <h3 style=\"color: #ef4444; font-size: 12px; font-weight: 800; letter-spacing: 1px; margin: 0 0 15px 0; text-transform: uppercase;\">Target Profile</h3>\n" +
                "          <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"font-size: 13px; color: #94a3b8; line-height: 1.8;\">\n" +
                "            <tr>\n" +
                "              <td width=\"35%\" style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Name:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(displayName) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Email/Username:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(userEmail) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Failed Attempts:</td>\n" +
                "              <td style=\"color: #ef4444; font-weight: 700; padding: 4px 0;\">" + failedAttempts + "</td>\n" +
                "            </tr>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Login Details Card -->\n" +
                "        <div style=\"background-color: #030712; border-left: 4px solid #f59e0b; border-radius: 8px; padding: 20px; margin-bottom: 30px;\">\n" +
                "          <h3 style=\"color: #f59e0b; font-size: 12px; font-weight: 800; letter-spacing: 1px; margin: 0 0 15px 0; text-transform: uppercase;\">Telemetry of Last Attempt</h3>\n" +
                "          <table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"font-size: 13px; color: #94a3b8; line-height: 1.8;\">\n" +
                "            <tr>\n" +
                "              <td width=\"35%\" style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Timestamp:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(formattedTime) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">IP Address:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0; font-family: monospace;\">" + escapeHtml(ipAddress) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Browser:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(browser) + "</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td style=\"color: #4b5563; font-weight: 600; padding: 4px 0;\">Operating System:</td>\n" +
                "              <td style=\"color: #f3f4f6; font-weight: 500; padding: 4px 0;\">" + escapeHtml(operatingSystem) + "</td>\n" +
                "            </tr>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Action Buttons -->\n" +
                "        <table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 auto 10px auto;\">\n" +
                "          <tr>\n" +
                "            <td align=\"center\" style=\"padding: 10px;\">\n" +
                "              <a href=\"" + dashboardUrl + "\" style=\"display: inline-block; background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); color: #ffffff; font-size: 13px; font-weight: 700; text-decoration: none; padding: 12px 30px; border-radius: 8px; box-shadow: 0 4px 10px rgba(239, 68, 68, 0.3); text-transform: uppercase; letter-spacing: 0.5px;\">VIEW ADMIN DASHBOARD</a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "          <tr>\n" +
                "            <td align=\"center\" style=\"padding: 10px;\">\n" +
                "              <a href=\"" + securityLogsUrl + "\" style=\"display: inline-block; background-color: transparent; border: 1px solid rgba(239, 68, 68, 0.4); color: #ef4444; font-size: 13px; font-weight: 700; text-decoration: none; padding: 11px 30px; border-radius: 8px; text-transform: uppercase; letter-spacing: 0.5px;\">VIEW LOGIN ACTIVITY</a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "    <!-- Footer Section -->\n" +
                "    <tr>\n" +
                "      <td style=\"padding: 30px 40px; background-color: #020617; text-align: center; border-top: 1px solid rgba(239, 68, 68, 0.05);\">\n" +
                "        <p style=\"color: #475569; font-size: 11px; line-height: 1.5; margin: 0 0 10px 0;\">\n" +
                "          This is an automated security notification from RiskVision AI.<br>Do not reply to this automated message.\n" +
                "        </p>\n" +
                "        <p style=\"color: #334155; font-size: 9px; font-weight: 800; letter-spacing: 1.5px; margin: 0; text-transform: uppercase;\">\n" +
                "          © 2026 STITCH RISKVISION. ALL RIGHTS RESERVED.\n" +
                "        </p>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";

        // ── PLAIN TEXT FALLBACK ───────────────────────────────────────────────
        String plainText = "RISKVISION AI - SECURITY ALERT\n" +
                "================================\n" +
                "MULTIPLE FAILED LOGINS DETECTED\n" +
                "Status: ACCOUNT TEMPORARILY LOCKED\n\n" +
                "An account lockout has been triggered due to consecutive failed authentication attempts.\n\n" +
                "TARGET PROFILE:\n" +
                "--------------\n" +
                "Name: " + displayName + "\n" +
                "Email: " + userEmail + "\n" +
                "Failed Attempts: " + failedAttempts + "\n\n" +
                "TELEMETRY OF LAST ATTEMPT:\n" +
                "--------------------------\n" +
                "Timestamp: " + formattedTime + "\n" +
                "IP Address: " + ipAddress + "\n" +
                "Browser: " + browser + "\n" +
                "OS: " + operatingSystem + "\n\n" +
                "ACTIONS:\n" +
                "--------\n" +
                "Admin Dashboard: " + dashboardUrl + "\n" +
                "Login Activity Logs: " + securityLogsUrl + "\n\n" +
                "This is an automated security notification from RiskVision AI. Do not reply to this message.";

        sendHtmlEmail(adminNotificationEmail, "⚠️ Security Alert — Multiple Failed Login Attempts", htmlContent, plainText);
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

        // Block known non-routable/dummy/internal domains
        if (domain.equals("riskvision.ai") || domain.equals("example.com")
                || domain.equals("localhost") || domain.equals("test.com")
                || domain.equals("mailtest.com") || domain.endsWith(".local")
                || domain.equals("placeholder.com") || domain.endsWith("noreply.github.com")) {
            log.info("ℹ️ [LOGIN_NOTIFICATION_SKIPPED_DUMMY_DOMAIN] Skipping email dispatch to {} — domain '{}' has no inbound mail server. " +
                     "Set ADMIN_NOTIFICATION_EMAIL in environment variables to a real address to enable live SMTP delivery.", email, domain);
            return false;
        }

        return true;
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendHtmlEmail(to, subject, htmlContent, "Please view this email in an HTML-compatible email client.");
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent, String plainTextContent) {
        // Skip sending to addresses on domains with no real mail server to avoid bounce errors
        if (!isDeliverableEmail(to)) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextContent, htmlContent);

            mailSender.send(message);
            log.info("Email successfully sent to {}", to);
        } catch (Exception e) {
            // Log the failure but do NOT throw — email delivery failures should never crash
            // the main request flow (login, registration, OAuth callback, etc.)
            log.error("Failed to send email to {}: {}. " +
                      "Check SMTP credentials and verify the recipient address is valid.", to, e.getMessage());
        }
    }
}

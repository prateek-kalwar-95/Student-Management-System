import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import javax.naming.CommunicationException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * SERVICE: Handles SMTP email dispatch with attachments via Jakarta Mail.
 * Supports Gmail SMTP and external configuration without exposing credentials.
 */
public class EmailService {

    public static final String CONFIG_FILE = "smtp.properties";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private static final Map<String, String> COMMON_DOMAIN_TYPOS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"),
            Map.entry("gmal.com", "gmail.com"),
            Map.entry("gmai.com", "gmail.com"),
            Map.entry("gnail.com", "gmail.com"),
            Map.entry("gmil.com", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"),
            Map.entry("gmail.om", "gmail.com"),
            Map.entry("yaho.com", "yahoo.com"),
            Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("yhoo.com", "yahoo.com"),
            Map.entry("hotmal.com", "hotmail.com"),
            Map.entry("hotmai.com", "hotmail.com"),
            Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("outlok.com", "outlook.com"),
            Map.entry("outloo.com", "outlook.com"),
            Map.entry("outlook.con", "outlook.com"),
            Map.entry("icoud.com", "icloud.com"),
            Map.entry("icloud.con", "icloud.com")
    );

    public enum DomainVerificationStatus {
        VALID,
        INVALID_SYNTAX,
        INVALID_DOMAIN,
        NETWORK_UNAVAILABLE
    }

    public static class DomainCheckResult {
        private final DomainVerificationStatus status;
        private final String domain;
        private final String suggestedDomain;
        private final String message;

        public DomainCheckResult(DomainVerificationStatus status, String domain, String suggestedDomain, String message) {
            this.status = status;
            this.domain = domain;
            this.suggestedDomain = suggestedDomain;
            this.message = message;
        }

        public DomainVerificationStatus getStatus() { return status; }
        public String getDomain() { return domain; }
        public String getSuggestedDomain() { return suggestedDomain; }
        public String getMessage() { return message; }
    }

    /**
     * Checks whether recipient email has a valid syntax.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        if (trimmed.isEmpty() || trimmed.contains("..")) return false;
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) return false;

        int at = trimmed.indexOf('@');
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        if (local.startsWith(".") || local.endsWith(".")) return false;
        if (domain.startsWith(".") || domain.endsWith(".")) return false;

        for (String label : domain.split("\\.")) {
            if (label.isEmpty() || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts domain after '@' in lowercase, or empty string if missing.
     */
    public static String extractDomain(String email) {
        if (email == null) return "";
        int at = email.trim().lastIndexOf('@');
        if (at < 0 || at == email.trim().length() - 1) return "";
        return email.trim().substring(at + 1).toLowerCase();
    }

    /**
     * Suggests a likely domain correction for common typos (e.g. gmial.com -> gmail.com).
     */
    public static String suggestDomainTypo(String email) {
        String domain = extractDomain(email);
        return COMMON_DOMAIN_TYPOS.get(domain);
    }

    /**
     * Checks whether the email uses a reserved/placeholder domain (e.g. example.com, test.com)
     * that cannot receive real emails.
     */
    public static boolean isReservedDomainEmail(String email) {
        String domain = extractDomain(email);
        return domain.equals("example.com") || domain.equals("example.org") || domain.equals("example.net")
                || domain.equals("test.com") || domain.equals("invalid");
    }

    /**
     * LEVEL 2: Verifies that the email domain exists and accepts email via DNS MX (with A/AAAA fallback).
     */
    public static DomainCheckResult verifyEmailDomain(String email) {
        if (!isValidEmail(email)) {
            return new DomainCheckResult(
                    DomainVerificationStatus.INVALID_SYNTAX,
                    "",
                    null,
                    "Email syntax is invalid."
            );
        }

        String domain = extractDomain(email);
        String typoSuggestion = COMMON_DOMAIN_TYPOS.get(domain);
        if (isReservedDomainEmail(email)) {
            return new DomainCheckResult(
                    DomainVerificationStatus.INVALID_DOMAIN,
                    domain,
                    typoSuggestion,
                    "Reserved/placeholder domain '@" + domain + "' cannot receive real emails."
            );
        }

        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttr = attrs.get("MX");
            if (mxAttr != null && mxAttr.size() > 0) {
                for (int i = 0; i < mxAttr.size(); i++) {
                    String val = String.valueOf(mxAttr.get(i)).trim();
                    // RFC 7505 Null MX record ("0 .") explicitly indicates domain accepts no mail
                    if (!val.matches("^0\\s+\\.$")) {
                        return new DomainCheckResult(
                                DomainVerificationStatus.VALID,
                                domain,
                                typoSuggestion,
                                "Mail server (MX) verified for @" + domain + "."
                        );
                    }
                }
                return new DomainCheckResult(
                        DomainVerificationStatus.INVALID_DOMAIN,
                        domain,
                        typoSuggestion,
                        "Domain '@" + domain + "' explicitly rejects incoming mail (Null MX)."
                );
            }

            // Fallback per RFC 5321: check A or AAAA record if no MX record exists
            Attributes hostAttrs = ctx.getAttributes(domain, new String[]{"A", "AAAA"});
            if ((hostAttrs.get("A") != null && hostAttrs.get("A").size() > 0)
                    || (hostAttrs.get("AAAA") != null && hostAttrs.get("AAAA").size() > 0)) {
                return new DomainCheckResult(
                        DomainVerificationStatus.VALID,
                        domain,
                        typoSuggestion,
                        "Domain host verified for @" + domain + "."
                );
            }

            return new DomainCheckResult(
                    DomainVerificationStatus.INVALID_DOMAIN,
                    domain,
                    typoSuggestion,
                    "No mail server (MX) or host record found for '@" + domain + "'."
            );
        } catch (NameNotFoundException ex) {
            return new DomainCheckResult(
                    DomainVerificationStatus.INVALID_DOMAIN,
                    domain,
                    typoSuggestion,
                    "Domain '@" + domain + "' does not exist."
            );
        } catch (CommunicationException | ServiceUnavailableException ex) {
            return new DomainCheckResult(
                    DomainVerificationStatus.NETWORK_UNAVAILABLE,
                    domain,
                    typoSuggestion,
                    "Unable to reach DNS server (offline or firewall blocked)."
            );
        } catch (NamingException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("timeout") || msg.contains("dns") || msg.contains("unreachable")) {
                return new DomainCheckResult(
                        DomainVerificationStatus.NETWORK_UNAVAILABLE,
                        domain,
                        typoSuggestion,
                        "DNS lookup timed out or network is unavailable."
                );
            }
            return new DomainCheckResult(
                    DomainVerificationStatus.INVALID_DOMAIN,
                    domain,
                    typoSuggestion,
                    "Could not verify domain '@" + domain + "': " + ex.getMessage()
            );
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /**
     * LEVEL 3: Generates a cryptographically random 6-digit OTP code.
     */
    public static String generateOtp() {
        int code = 100000 + OTP_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * LEVEL 3: Sends a 6-digit verification OTP to confirm student inbox ownership.
     */
    public static void sendVerificationOtpEmail(String studentName, String recipientEmail, String otp)
            throws MessagingException, IOException {
        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid recipient email address: '" + recipientEmail + "'.");
        }
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code cannot be empty.");
        }

        Properties config = loadSmtpProperties();
        final String username = config.getProperty("mail.smtp.user");
        final String password = config.getProperty("mail.smtp.password");
        final String from = config.getProperty("mail.smtp.from", username);

        if (isPlaceholder(username) || isPlaceholder(password)) {
            throw new MessagingException("SMTP configuration is incomplete. Please configure sender email settings first.");
        }

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        Session session = Session.getInstance(config, auth);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from, "Student Management System"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail.trim()));
        message.setSubject("Email Verification Code (OTP): " + otp + " - Student Management System", "UTF-8");

        String safeDisplayName = (studentName == null || studentName.trim().isEmpty()) ? "Student" : studentName.trim();
        String plain = "Hello " + safeDisplayName + ",\n\n"
                + "Your email verification code (OTP) for the Student Management System is:\n\n"
                + "    " + otp + "\n\n"
                + "Please share or enter this 6-digit code in the application to verify your email address.\n"
                + "If you did not request this verification, you can safely ignore this email.\n\n"
                + "Regards,\nStudent Management System";

        String html = "<!DOCTYPE html><html><body style='margin:0;padding:24px;background-color:#F4F6F9;font-family:Segoe UI,Arial,sans-serif;color:#1F2937;'>"
                + "<table role='presentation' width='100%' cellspacing='0' cellpadding='0'><tr><td align='center'>"
                + "<table role='presentation' width='520' cellspacing='0' cellpadding='0' style='max-width:520px;width:100%;background:#FFFFFF;border-radius:10px;overflow:hidden;border:1px solid #E5E7EB;'>"
                + "<tr><td style='background-color:#182B49;padding:18px 24px;color:#FFFFFF;'>"
                + "<div style='font-size:18px;font-weight:700;'>Student Email Verification</div>"
                + "<div style='font-size:12px;color:#CBD5E1;margin-top:3px;'>Student Management System</div></td></tr>"
                + "<tr><td style='padding:24px;'>"
                + "<p style='margin:0 0 12px 0;font-size:14px;'>Hello <strong>" + escapeHtml(safeDisplayName) + "</strong>,</p>"
                + "<p style='margin:0 0 18px 0;font-size:14px;line-height:1.5;color:#374151;'>"
                + "Use the following 6-digit One-Time Password (OTP) to verify your student email address:</p>"
                + "<div style='background-color:#F0F9FF;border:1px dashed #0284C7;border-radius:8px;padding:16px;text-align:center;margin-bottom:18px;'>"
                + "<div style='font-size:28px;font-weight:700;letter-spacing:6px;color:#0369A1;'>" + escapeHtml(otp) + "</div>"
                + "</div>"
                + "<p style='margin:0;font-size:12px;color:#6B7280;'>If you did not request this verification, please ignore this email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";

        Multipart alt = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plain, "UTF-8");
        alt.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        alt.addBodyPart(htmlPart);

        message.setContent(alt);
        Transport.send(message);
    }

    private static boolean isPlaceholder(String value) {
        if (value == null) return true;
        String v = value.trim().toLowerCase();
        return v.isEmpty()
                || v.equals("your_email@gmail.com")
                || v.equals("youremail@gmail.com")
                || v.equals("your_16_digit_app_password")
                || v.equals("your_16_digit_gmail_app_password")
                || v.equals("your16charapppassword")
                || v.startsWith("your_");
    }

    /**
     * Resolves smtp.properties across the current working directory or project root
     * (handles running from IDE 'bin/' folder as well as project root).
     */
    public static File resolveConfigFileForRead() {
        File cwdFile = new File(CONFIG_FILE);
        if (cwdFile.exists() && cwdFile.isFile()) {
            return cwdFile;
        }
        try {
            File codeDir = new File(EmailService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (codeDir.isFile()) {
                codeDir = codeDir.getParentFile();
            }
            if (codeDir != null) {
                File inCodeDir = new File(codeDir, CONFIG_FILE);
                if (inCodeDir.exists() && inCodeDir.isFile()) {
                    return inCodeDir;
                }
                if ("bin".equalsIgnoreCase(codeDir.getName()) && codeDir.getParentFile() != null) {
                    File inParent = new File(codeDir.getParentFile(), CONFIG_FILE);
                    if (inParent.exists() && inParent.isFile()) {
                        return inParent;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        File parentFile = new File("..", CONFIG_FILE);
        if (parentFile.exists() && parentFile.isFile()) {
            return parentFile;
        }
        return cwdFile;
    }

    public static File resolveConfigFileForWrite() {
        File cwd = new File(System.getProperty("user.dir", "."));
        if ("bin".equalsIgnoreCase(cwd.getName()) && cwd.getParentFile() != null
                && new File(cwd.getParentFile(), "EmailService.java").exists()) {
            return new File(cwd.getParentFile(), CONFIG_FILE);
        }
        return new File(CONFIG_FILE);
    }

    private static String normalizePassword(String host, String password) {
        if (password == null) return "";
        String trimmed = password.trim();
        if (host != null && host.toLowerCase().contains("gmail.com")) {
            return trimmed.replaceAll("\\s+", "");
        }
        return trimmed;
    }

    /**
     * Loads SMTP configuration according to priority:
     * 1. First check environment variables (SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS).
     * 2. If environment variables are unavailable, attempt to load smtp.properties.
     */
    public static Properties loadSmtpProperties() {
        Properties props = new Properties();

        // Check Environment Variables first
        String envHost = getEnv("SMTP_HOST", null);
        String envPort = getEnv("SMTP_PORT", null);
        String envUser = getEnv("SMTP_USER", getEnv("SMTP_USERNAME", null));
        String envPass = getEnv("SMTP_PASS", getEnv("SMTP_PASSWORD", null));
        String envAuth = getEnv("SMTP_AUTH", null);
        String envStartTls = getEnv("SMTP_STARTTLS", null);

        boolean hasEnvCredentials = (!isPlaceholder(envUser) && !isPlaceholder(envPass));

        if (hasEnvCredentials) {
            String resolvedHost = envHost != null ? envHost : "smtp.gmail.com";
            props.setProperty("mail.smtp.host", resolvedHost);
            props.setProperty("mail.smtp.port", envPort != null ? envPort : "587");
            props.setProperty("mail.smtp.auth", envAuth != null ? envAuth : "true");
            props.setProperty("mail.smtp.starttls.enable", envStartTls != null ? envStartTls : "true");
            props.setProperty("mail.smtp.user", envUser.trim());
            props.setProperty("mail.smtp.password", normalizePassword(resolvedHost, envPass));
            props.setProperty("mail.smtp.from", envUser.trim());
        } else {
            // Environment variables unavailable: attempt to load smtp.properties
            File cfg = resolveConfigFileForRead();
            if (cfg.exists() && cfg.isFile()) {
                try (FileInputStream fis = new FileInputStream(cfg)) {
                    props.load(fis);
                } catch (IOException ignored) {
                }
            }

            // Defaults if missing in file
            if (!props.containsKey("mail.smtp.host") || props.getProperty("mail.smtp.host").trim().isEmpty()) {
                props.setProperty("mail.smtp.host", "smtp.gmail.com");
            } else {
                props.setProperty("mail.smtp.host", props.getProperty("mail.smtp.host").trim());
            }
            if (!props.containsKey("mail.smtp.port") || props.getProperty("mail.smtp.port").trim().isEmpty()) {
                props.setProperty("mail.smtp.port", "587");
            } else {
                props.setProperty("mail.smtp.port", props.getProperty("mail.smtp.port").trim());
            }
            if (!props.containsKey("mail.smtp.auth") || props.getProperty("mail.smtp.auth").trim().isEmpty()) {
                props.setProperty("mail.smtp.auth", "true");
            }
            if (!props.containsKey("mail.smtp.starttls.enable") || props.getProperty("mail.smtp.starttls.enable").trim().isEmpty()) {
                props.setProperty("mail.smtp.starttls.enable", "true");
            }

            String host = props.getProperty("mail.smtp.host");
            String fileUser = props.getProperty("mail.smtp.user");
            if (fileUser != null && !fileUser.trim().isEmpty()) {
                props.setProperty("mail.smtp.user", fileUser.trim());
                if (!props.containsKey("mail.smtp.from") || props.getProperty("mail.smtp.from").trim().isEmpty()) {
                    props.setProperty("mail.smtp.from", fileUser.trim());
                }
            }
            String filePass = props.getProperty("mail.smtp.password");
            if (filePass != null) {
                props.setProperty("mail.smtp.password", normalizePassword(host, filePass));
            }
        }

        // Standard connection timeouts
        props.putIfAbsent("mail.smtp.connectiontimeout", "10000");
        props.putIfAbsent("mail.smtp.timeout", "15000");

        return props;
    }

    /**
     * Saves SMTP configuration to smtp.properties in the project root folder.
     */
    public static File saveSmtpProperties(String host, String port, String user, String password) throws IOException {
        String cleanHost = (host == null || host.trim().isEmpty()) ? "smtp.gmail.com" : host.trim();
        String cleanPort = (port == null || port.trim().isEmpty()) ? "587" : port.trim();
        String cleanUser = user == null ? "" : user.trim();
        String cleanPass = normalizePassword(cleanHost, password);

        Properties props = new Properties();
        props.setProperty("mail.smtp.host", cleanHost);
        props.setProperty("mail.smtp.port", cleanPort);
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.user", cleanUser);
        props.setProperty("mail.smtp.password", cleanPass);
        props.setProperty("mail.smtp.from", cleanUser);

        File target = resolveConfigFileForWrite();
        try (FileOutputStream fos = new FileOutputStream(target)) {
            props.store(fos, "SMTP Email Configuration for Student Management System (Do not commit to Git)");
        }
        return target;
    }

    private static String getEnv(String name, String fallback) {
        String val = System.getenv(name);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    /**
     * Checks if minimum required SMTP configuration is present and not a placeholder.
     */
    public static boolean isConfigured() {
        Properties p = loadSmtpProperties();
        String host = p.getProperty("mail.smtp.host");
        String user = p.getProperty("mail.smtp.user");
        String pass = p.getProperty("mail.smtp.password");

        return host != null && !host.trim().isEmpty()
                && !isPlaceholder(user)
                && !isPlaceholder(pass);
    }

    /**
     * Sends the individual student's academic report via email using rich StudentReportData
     * for a personalized HTML summary and plain-text fallback alongside the PDF attachment.
     */
    public static void sendReportEmail(StudentReportData data, File pdfAttachment)
            throws MessagingException, IOException {
        if (data == null) {
            throw new IllegalArgumentException("Student report data cannot be null.");
        }
        sendReportEmailInternal(data.getName(), data.getEmail(), data.getRollNo(), data, pdfAttachment);
    }

    /**
     * FEATURE 4 & TASK 3: Sends the individual student's academic report via email.
     * Consistent signature: (studentName, recipientEmail, rollNo, pdfAttachment).
     *
     * @param studentName    student's full name
     * @param recipientEmail student's registered email address
     * @param rollNo         student's roll number
     * @param pdfAttachment  generated report PDF file
     * @throws MessagingException if SMTP communication fails
     * @throws IOException        if file reading fails
     */
    public static void sendReportEmail(String studentName, String recipientEmail, long rollNo, File pdfAttachment)
            throws MessagingException, IOException {

        // Auto-detect and handle if arguments were passed in reverse order (email, name)
        String actualName = studentName;
        String actualEmail = recipientEmail;
        if (studentName != null && studentName.contains("@") && (recipientEmail == null || !recipientEmail.contains("@"))) {
            actualEmail = studentName;
            actualName = recipientEmail;
        }
        sendReportEmailInternal(actualName, actualEmail, rollNo, null, pdfAttachment);
    }

    /**
     * Sends a targeted Low-Attendance Alert email (with optional PDF report attachment)
     * for students falling below the 75% attendance requirement.
     */
    public static void sendLowAttendanceAlertEmail(StudentReportData data, File pdfAttachment)
            throws MessagingException, IOException {
        if (data == null) {
            throw new IllegalArgumentException("Student report data cannot be null.");
        }
        String recipientEmail = data.getEmail();
        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid recipient email address: '" + recipientEmail + "'.");
        }
        if (pdfAttachment != null && !pdfAttachment.exists()) {
            throw new IOException("PDF report attachment file is missing or was not generated.");
        }

        boolean critical = data.getDangerCount() > 0 || data.getOverallAttendancePct() < 50.0;
        String subject = (critical
                ? "URGENT: Critical Attendance Alert (< 50%) - "
                : "Attendance Shortage Alert (< 75%) - ")
                + data.getName() + " - " + data.getRollNo();

        String plainBody = buildAlertPlainTextBody(data);
        String htmlBody = buildAlertHtmlBody(data, pdfAttachment != null);
        String attachName = pdfAttachment != null ? ("Academic_Report_" + data.getRollNo() + ".pdf") : null;

        dispatchMultipartMessage(recipientEmail.trim(), subject, plainBody, htmlBody, pdfAttachment, attachName);
    }

    private static void sendReportEmailInternal(
            String studentName,
            String recipientEmail,
            long rollNo,
            StudentReportData data,
            File pdfAttachment) throws MessagingException, IOException {

        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid recipient email address: '" + recipientEmail + "'. Please verify the student's email.");
        }

        if (pdfAttachment == null || !pdfAttachment.exists()) {
            throw new IOException("PDF report attachment file is missing or was not generated.");
        }

        String subject = "Academic Performance Report - " + studentName + " - " + rollNo;
        String plainBody = buildPlainTextBody(studentName, rollNo, data);
        String htmlBody = buildHtmlBody(studentName, rollNo, data);

        dispatchMultipartMessage(
                recipientEmail.trim(),
                subject,
                plainBody,
                htmlBody,
                pdfAttachment,
                "Academic_Report_" + rollNo + ".pdf");
    }

    private static void dispatchMultipartMessage(
            String recipientEmail,
            String subject,
            String plainBody,
            String htmlBody,
            File pdfAttachment,
            String attachmentFileName) throws MessagingException, IOException {

        Properties config = loadSmtpProperties();
        final String username = config.getProperty("mail.smtp.user");
        final String password = config.getProperty("mail.smtp.password");
        final String from = config.getProperty("mail.smtp.from", username);

        if (isPlaceholder(username) || isPlaceholder(password)) {
            throw new MessagingException("SMTP configuration is incomplete. Please configure 'smtp.properties' or environment variables with valid username and password.");
        }

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        Session session = Session.getInstance(config, auth);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from, "Student Management System"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject(subject, "UTF-8");

        Multipart alternativeMultipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plainBody, "UTF-8");
        alternativeMultipart.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        alternativeMultipart.addBodyPart(htmlPart);

        if (pdfAttachment != null) {
            Multipart mixedMultipart = new MimeMultipart("mixed");
            MimeBodyPart bodyContainer = new MimeBodyPart();
            bodyContainer.setContent(alternativeMultipart);
            mixedMultipart.addBodyPart(bodyContainer);

            MimeBodyPart attachmentPart = new MimeBodyPart();
            FileDataSource source = new FileDataSource(pdfAttachment);
            attachmentPart.setDataHandler(new DataHandler(source));
            attachmentPart.setFileName(attachmentFileName != null ? attachmentFileName : pdfAttachment.getName());
            mixedMultipart.addBodyPart(attachmentPart);

            message.setContent(mixedMultipart);
        } else {
            message.setContent(alternativeMultipart);
        }

        Transport.send(message);
    }

    private static String buildAlertPlainTextBody(StudentReportData data) {
        StringBuilder sb = new StringBuilder();
        boolean critical = data.getDangerCount() > 0 || data.getOverallAttendancePct() < 50.0;
        sb.append("Dear ").append(data.getName()).append(",\n\n");
        sb.append(critical
                ? "URGENT NOTICE: Your class attendance has dropped to a CRITICAL level (< 50%).\n\n"
                : "ATTENDANCE SHORTAGE NOTICE: Your class attendance is below the mandatory 75% minimum.\n\n");

        sb.append("STUDENT DETAILS\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Roll Number        : ").append(data.getRollNo()).append("\n");
        sb.append("Department         : ").append(data.getDepartment()).append("\n");
        sb.append(String.format("Overall Attendance : %.1f%% (%s)\n", data.getOverallAttendancePct(), data.getOverallStatus()));
        sb.append("Shortage Courses   : ").append(data.getShortageCount()).append("\n");
        sb.append("--------------------------------------------------\n\n");

        sb.append("COURSES BELOW 75% ATTENDANCE\n");
        for (StudentReportData.CoursePerformance cp : data.getCourses()) {
            if (cp.getAttendancePct() < 75.0) {
                sb.append(String.format("• %s - %s : %.1f%% (%s | Attendance Marks: 0/10)\n",
                        cp.getCourseCode(), cp.getCourseName(), cp.getAttendancePct(), cp.getStatus()));
            }
        }
        sb.append("\n");
        sb.append("Action Required:\n");
        sb.append(critical
                ? "Please meet your faculty advisor / Head of Department immediately to avoid examination debarment.\n\n"
                : "Courses below 75% earn 0/10 attendance marks. Please attend upcoming classes regularly to qualify for attendance marks.\n\n");
        sb.append("Regards,\nOffice of the Academic Registrar\nStudent Management System\n");
        return sb.toString();
    }

    private static String buildAlertHtmlBody(StudentReportData data, boolean hasAttachment) {
        boolean critical = data.getDangerCount() > 0 || data.getOverallAttendancePct() < 50.0;
        String headerBg = critical ? "#991B1B" : "#92400E";
        String calloutBg = critical ? "#F8D7DA" : "#FFF3CD";
        String calloutBorder = critical ? "#F5C2C7" : "#FFECB5";
        String calloutText = critical ? "#842029" : "#664D03";
        String headline = critical ? "Critical Attendance Alert (&lt; 50%)" : "Attendance Shortage Alert (&lt; 75%)";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><body style='margin:0;padding:0;background-color:#F4F6F9;font-family:Segoe UI,Arial,sans-serif;color:#1F2937;'>");
        html.append("<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color:#F4F6F9;padding:24px 12px;'>");
        html.append("<tr><td align='center'>");
        html.append("<table role='presentation' width='640' cellspacing='0' cellpadding='0' style='max-width:640px;width:100%;background-color:#FFFFFF;border-radius:10px;overflow:hidden;border:1px solid #E5E7EB;'>");

        html.append("<tr><td style='background-color:").append(headerBg).append(";padding:20px 26px;color:#FFFFFF;'>");
        html.append("<div style='font-size:20px;font-weight:700;'>").append(headline).append("</div>");
        html.append("<div style='font-size:12px;color:#FDE68A;margin-top:4px;'>Automated Academic Attendance Monitoring &bull; Student Management System</div>");
        html.append("</td></tr>");

        html.append("<tr><td style='padding:22px 26px;'>");
        html.append("<p style='margin:0 0 12px 0;font-size:15px;'>Dear <strong>").append(escapeHtml(data.getName())).append("</strong>,</p>");
        html.append("<div style='background-color:").append(calloutBg).append(";border:1px solid ").append(calloutBorder)
            .append(";color:").append(calloutText).append(";border-radius:6px;padding:12px 14px;margin-bottom:16px;font-size:13px;line-height:1.5;'>");
        if (critical) {
            html.append("<strong>URGENT ACTION REQUIRED:</strong> You have <strong>").append(data.getDangerCount())
                .append("</strong> course(s) in critical attendance danger (&lt; 50%) and an overall attendance of <strong>")
                .append(String.format("%.1f%%", data.getOverallAttendancePct()))
                .append("</strong>. Please meet your academic advisor immediately.");
        } else {
            html.append("<strong>SHORTAGE NOTICE:</strong> You currently have <strong>").append(data.getShortageCount())
                .append("</strong> course(s) below the mandatory <strong>75%</strong> attendance threshold. Courses below 75% receive <strong>0 / 10</strong> attendance marks.");
        }
        html.append("</div>");

        html.append("<table width='100%' cellspacing='0' cellpadding='0' style='background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:8px;margin-bottom:16px;'><tr>");
        html.append("<td style='padding:10px 14px;font-size:12px;width:34%;'><div style='color:#64748B;font-size:11px;'>ROLL NUMBER</div><div style='font-weight:700;margin-top:2px;'>")
            .append(data.getRollNo()).append("</div></td>");
        html.append("<td style='padding:10px 14px;font-size:12px;width:33%;border-left:1px solid #E2E8F0;'><div style='color:#64748B;font-size:11px;'>DEPARTMENT</div><div style='font-weight:700;margin-top:2px;'>")
            .append(escapeHtml(data.getDepartment())).append("</div></td>");
        html.append("<td style='padding:10px 14px;font-size:12px;width:33%;border-left:1px solid #E2E8F0;'><div style='color:#64748B;font-size:11px;'>OVERALL ATTENDANCE</div><div style='font-weight:700;margin-top:2px;color:")
            .append(getStatusTextColor(data.getOverallStatus())).append(";'>").append(String.format("%.1f%%", data.getOverallAttendancePct())).append("</div></td>");
        html.append("</tr></table>");

        html.append("<div style='font-size:13px;font-weight:700;color:#182B49;margin-bottom:8px;'>Courses Requiring Immediate Attendance Improvement</div>");
        html.append("<table width='100%' cellspacing='0' cellpadding='8' style='border-collapse:collapse;font-size:12px;border:1px solid #E2E8F0;margin-bottom:16px;'>");
        html.append("<tr style='background-color:#F1F5F9;color:#1E293B;text-align:left;border-bottom:1px solid #CBD5E1;'>")
            .append("<th>Code</th><th>Course Name</th><th style='text-align:center;'>Current Att.</th><th style='text-align:center;'>Att. Marks</th><th style='text-align:center;'>Severity</th></tr>");

        for (StudentReportData.CoursePerformance cp : data.getCourses()) {
            if (cp.getAttendancePct() < 75.0) {
                String rowBg = getStatusBgColor(cp.getStatus());
                String rowFg = getStatusTextColor(cp.getStatus());
                html.append("<tr style='border-bottom:1px solid #E2E8F0;'>")
                    .append("<td style='font-weight:600;'>").append(escapeHtml(cp.getCourseCode())).append("</td>")
                    .append("<td>").append(escapeHtml(cp.getCourseName())).append("</td>")
                    .append("<td style='text-align:center;font-weight:700;color:").append(rowFg).append(";'>")
                    .append(String.format("%.1f%%", cp.getAttendancePct())).append("</td>")
                    .append("<td style='text-align:center;'>0.0 / 10</td>")
                    .append("<td style='text-align:center;'><span style='display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:700;background-color:")
                    .append(rowBg).append(";color:").append(rowFg).append(";'>").append(escapeHtml(cp.getStatus())).append("</span></td>")
                    .append("</tr>");
            }
        }
        html.append("</table>");

        if (hasAttachment) {
            html.append("<p style='margin:0 0 14px 0;font-size:13px;color:#4B5563;'>")
                .append("Your complete Academic Performance Report (<code>Academic_Report_")
                .append(data.getRollNo()).append(".pdf</code>) is also attached for reference.</p>");
        }

        html.append("<p style='margin:0;font-size:13px;line-height:1.6;color:#1F2937;'>Regards,<br>")
            .append("<strong>Office of the Academic Registrar</strong><br>")
            .append("<span style='color:#6B7280;'>Student Management System</span></p>");
        html.append("</td></tr>");

        html.append("<tr><td style='background-color:#F8FAFC;border-top:1px solid #E5E7EB;padding:12px 26px;font-size:11px;color:#6B7280;text-align:center;'>")
            .append("Automated Low-Attendance Alert &bull; Student Management System")
            .append("</td></tr>");
        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private static String buildPlainTextBody(String studentName, long rollNo, StudentReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(studentName).append(",\n\n");
        sb.append("Please find attached your official Academic Performance Report (PDF) from the Student Management System.\n\n");

        if (data != null) {
            sb.append("ACADEMIC SUMMARY\n");
            sb.append("--------------------------------------------------\n");
            sb.append("Student Name       : ").append(data.getName()).append("\n");
            sb.append("Roll Number        : ").append(data.getRollNo()).append(" (").append(data.getRegistrationNo()).append(")\n");
            sb.append("Department         : ").append(data.getDepartment()).append("\n");
            sb.append("Enrolled Courses   : ").append(data.getTotalCourses()).append("\n");
            sb.append(String.format("Overall Attendance : %.1f%% (%s)\n",
                    data.getOverallAttendancePct(), data.getOverallStatus()));
            sb.append(String.format("Overall Score      : %.1f / 100 (Grade: %s)\n",
                    data.getOverallAverageScore(), data.getOverallGrade()));
            sb.append("Generated On       : ").append(data.getGeneratedTimestamp()).append("\n");
            sb.append("--------------------------------------------------\n\n");

            if (!data.getCourses().isEmpty()) {
                sb.append("COURSE-WISE PERFORMANCE\n");
                for (StudentReportData.CoursePerformance cp : data.getCourses()) {
                    sb.append(String.format("• %s - %s | Att: %.1f%% | Total: %.1f/100 | Grade: %s (%s)\n",
                            cp.getCourseCode(), cp.getCourseName(),
                            cp.getAttendancePct(), cp.getTotalMarks(), cp.getGrade(), cp.getStatus()));
                }
                sb.append("\n");
            }

            if ("DANGER".equals(data.getOverallStatus())) {
                sb.append("IMPORTANT NOTICE (CRITICAL ATTENDANCE < 50%):\n");
                sb.append("Your attendance is in the critical zone. Please contact your academic advisor immediately to avoid examination debarment.\n\n");
            } else if ("WARNING".equals(data.getOverallStatus())) {
                sb.append("ATTENDANCE REMINDER (< 75%):\n");
                sb.append("One or more courses are below the mandatory 75% attendance requirement. Please attend upcoming sessions regularly.\n\n");
            } else {
                sb.append("ATTENDANCE STATUS: SAFE (>= 75%)\n");
                sb.append("Great work! Your attendance meets all institutional requirements.\n\n");
            }
        } else {
            sb.append("Roll Number: ").append(rollNo).append("\n\n");
            sb.append("The attached PDF contains your personal details, enrolled courses, attendance, examination marks, and overall grades.\n\n");
        }

        sb.append("Attached File: Academic_Report_").append(rollNo).append(".pdf\n\n");
        sb.append("Warm regards,\n");
        sb.append("Office of the Academic Registrar\n");
        sb.append("Student Management System\n");
        return sb.toString();
    }

    private static String buildHtmlBody(String studentName, long rollNo, StudentReportData data) {
        String safeName = escapeHtml(studentName);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><body style='margin:0;padding:0;background-color:#F4F6F9;font-family:Segoe UI,Arial,sans-serif;color:#1F2937;'>");
        html.append("<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color:#F4F6F9;padding:24px 12px;'>");
        html.append("<tr><td align='center'>");
        html.append("<table role='presentation' width='640' cellspacing='0' cellpadding='0' style='max-width:640px;width:100%;background-color:#FFFFFF;border-radius:10px;overflow:hidden;border:1px solid #E5E7EB;'>");

        // Header Banner
        html.append("<tr><td style='background-color:#182B49;padding:22px 28px;color:#FFFFFF;'>");
        html.append("<div style='font-size:20px;font-weight:700;letter-spacing:0.3px;'>Academic Performance Report</div>");
        html.append("<div style='font-size:12px;color:#CBD5E1;margin-top:4px;'>Office of the Academic Registrar &bull; Student Management System</div>");
        html.append("</td></tr>");

        // Greeting
        html.append("<tr><td style='padding:24px 28px 10px 28px;'>");
        html.append("<p style='margin:0 0 12px 0;font-size:15px;'>Dear <strong>").append(safeName).append("</strong>,</p>");
        html.append("<p style='margin:0 0 16px 0;font-size:14px;line-height:1.6;color:#374151;'>");
        html.append("Please find attached your official <strong>Academic Performance Report</strong> (<code>Academic_Report_")
            .append(rollNo).append(".pdf</code>) generated by the Student Management System.");
        html.append("</p>");

        if (data != null) {
            // Student Info Strip
            html.append("<table width='100%' cellspacing='0' cellpadding='0' style='background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:8px;margin-bottom:18px;'>");
            html.append("<tr>");
            html.append("<td style='padding:12px 14px;font-size:12px;color:#475569;width:33%;'>")
                .append("<div style='color:#64748B;font-size:11px;text-transform:uppercase;'>Roll / Reg No</div>")
                .append("<div style='font-weight:700;color:#0F172A;font-size:13px;margin-top:2px;'>")
                .append(data.getRollNo()).append(" &bull; ").append(escapeHtml(data.getRegistrationNo())).append("</div></td>");
            html.append("<td style='padding:12px 14px;font-size:12px;color:#475569;width:34%;border-left:1px solid #E2E8F0;'>")
                .append("<div style='color:#64748B;font-size:11px;text-transform:uppercase;'>Department</div>")
                .append("<div style='font-weight:700;color:#0F172A;font-size:13px;margin-top:2px;'>")
                .append(escapeHtml(data.getDepartment())).append("</div></td>");
            html.append("<td style='padding:12px 14px;font-size:12px;color:#475569;width:33%;border-left:1px solid #E2E8F0;'>")
                .append("<div style='color:#64748B;font-size:11px;text-transform:uppercase;'>Generated On</div>")
                .append("<div style='font-weight:700;color:#0F172A;font-size:13px;margin-top:2px;'>")
                .append(escapeHtml(data.getGeneratedTimestamp())).append("</div></td>");
            html.append("</tr></table>");

            // KPI Cards Row
            String attColor = getStatusTextColor(data.getOverallStatus());
            html.append("<table width='100%' cellspacing='0' cellpadding='0' style='margin-bottom:18px;'><tr>");
            html.append("<td width='32%' style='background:#FFFFFF;border:1px solid #E2E8F0;border-top:3px solid #2980B9;border-radius:6px;padding:12px;text-align:center;'>")
                .append("<div style='font-size:11px;color:#64748B;'>Enrolled Courses</div>")
                .append("<div style='font-size:18px;font-weight:700;color:#2980B9;margin-top:4px;'>").append(data.getTotalCourses()).append("</div></td>");
            html.append("<td width='2%'></td>");
            html.append("<td width='32%' style='background:#FFFFFF;border:1px solid #E2E8F0;border-top:3px solid ").append(attColor).append(";border-radius:6px;padding:12px;text-align:center;'>")
                .append("<div style='font-size:11px;color:#64748B;'>Overall Attendance</div>")
                .append("<div style='font-size:18px;font-weight:700;color:").append(attColor).append(";margin-top:4px;'>")
                .append(String.format("%.1f%%", data.getOverallAttendancePct())).append("</div></td>");
            html.append("<td width='2%'></td>");
            html.append("<td width='32%' style='background:#FFFFFF;border:1px solid #E2E8F0;border-top:3px solid #D35400;border-radius:6px;padding:12px;text-align:center;'>")
                .append("<div style='font-size:11px;color:#64748B;'>Overall Score</div>")
                .append("<div style='font-size:18px;font-weight:700;color:#D35400;margin-top:4px;'>")
                .append(String.format("%.1f (%s)", data.getOverallAverageScore(), escapeHtml(data.getOverallGrade()))).append("</div></td>");
            html.append("</tr></table>");

            // Dynamic Attendance Callout
            String bannerBg;
            String bannerBorder;
            String bannerText;
            String bannerTitle;
            String bannerMsg;
            if ("DANGER".equals(data.getOverallStatus())) {
                bannerBg = "#F8D7DA";
                bannerBorder = "#F5C2C7";
                bannerText = "#842029";
                bannerTitle = "CRITICAL ATTENDANCE ALERT (&lt; 50%)";
                bannerMsg = String.format("Your overall attendance is <strong>%.1f%%</strong> with <strong>%d</strong> course(s) below 50%%. Please contact your academic advisor immediately to avoid examination debarment.",
                        data.getOverallAttendancePct(), data.getDangerCount());
            } else if ("WARNING".equals(data.getOverallStatus())) {
                bannerBg = "#FFF3CD";
                bannerBorder = "#FFECB5";
                bannerText = "#664D03";
                bannerTitle = "ATTENDANCE SHORTAGE NOTICE (&lt; 75%)";
                bannerMsg = String.format("Your overall attendance is <strong>%.1f%%</strong> with <strong>%d</strong> course(s) below the mandatory 75%% threshold. Please attend upcoming classes regularly.",
                        data.getOverallAttendancePct(), data.getShortageCount());
            } else {
                bannerBg = "#D4EDDA";
                bannerBorder = "#BADBCC";
                bannerText = "#0F5132";
                bannerTitle = "ATTENDANCE STATUS: SAFE (&ge; 75%)";
                bannerMsg = String.format("Great work! You have maintained a satisfactory attendance record of <strong>%.1f%%</strong> across your enrolled courses.",
                        data.getOverallAttendancePct());
            }

            html.append("<div style='background-color:").append(bannerBg).append(";border:1px solid ").append(bannerBorder)
                .append(";color:").append(bannerText).append(";border-radius:6px;padding:12px 14px;margin-bottom:18px;font-size:13px;line-height:1.5;'>")
                .append("<div style='font-weight:700;margin-bottom:4px;'>").append(bannerTitle).append("</div>")
                .append("<div>").append(bannerMsg).append("</div></div>");

            // Course-Wise Performance Table
            if (!data.getCourses().isEmpty()) {
                html.append("<div style='font-size:13px;font-weight:700;color:#182B49;margin-bottom:8px;'>Course-Wise Performance Summary</div>");
                html.append("<table width='100%' cellspacing='0' cellpadding='8' style='border-collapse:collapse;font-size:12px;border:1px solid #E2E8F0;margin-bottom:18px;'>");
                html.append("<tr style='background-color:#F1F5F9;color:#1E293B;text-align:left;border-bottom:1px solid #CBD5E1;'>")
                    .append("<th>Code</th><th>Course Name</th><th style='text-align:center;'>Attendance</th>")
                    .append("<th style='text-align:center;'>Total</th><th style='text-align:center;'>Grade</th><th style='text-align:center;'>Status</th></tr>");

                for (StudentReportData.CoursePerformance cp : data.getCourses()) {
                    String rowBg = getStatusBgColor(cp.getStatus());
                    String rowFg = getStatusTextColor(cp.getStatus());
                    html.append("<tr style='border-bottom:1px solid #E2E8F0;'>")
                        .append("<td style='font-weight:600;'>").append(escapeHtml(cp.getCourseCode())).append("</td>")
                        .append("<td>").append(escapeHtml(cp.getCourseName())).append("</td>")
                        .append("<td style='text-align:center;'>").append(String.format("%.1f%%", cp.getAttendancePct())).append("</td>")
                        .append("<td style='text-align:center;'>").append(String.format("%.1f / 100", cp.getTotalMarks())).append("</td>")
                        .append("<td style='text-align:center;font-weight:700;'>").append(escapeHtml(cp.getGrade())).append("</td>")
                        .append("<td style='text-align:center;'><span style='display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:700;background-color:")
                        .append(rowBg).append(";color:").append(rowFg).append(";'>").append(escapeHtml(cp.getStatus())).append("</span></td>")
                        .append("</tr>");
                }
                html.append("</table>");
            }
        } else {
            html.append("<div style='background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;padding:12px 14px;margin-bottom:16px;font-size:13px;color:#374151;'>")
                .append("<strong>Roll Number:</strong> ").append(rollNo)
                .append("<br>The attached PDF contains your complete course-wise attendance, examination marks, and grade summary.</div>");
        }

        html.append("<p style='margin:0 0 16px 0;font-size:13px;line-height:1.6;color:#4B5563;'>");
        html.append("Please open the attached PDF document for the complete printable breakdown, including attendance weightage and grading policy.");
        html.append("</p>");

        html.append("<p style='margin:0;font-size:13px;line-height:1.6;color:#1F2937;'>Warm regards,<br>");
        html.append("<strong>Office of the Academic Registrar</strong><br>");
        html.append("<span style='color:#6B7280;'>Student Management System</span></p>");
        html.append("</td></tr>");

        // Footer
        html.append("<tr><td style='background-color:#F8FAFC;border-top:1px solid #E5E7EB;padding:12px 28px;font-size:11px;color:#6B7280;text-align:center;'>");
        html.append("This is an automated academic performance notification. Please contact your department coordinator for any discrepancies.");
        html.append("</td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private static String getStatusBgColor(String status) {
        if ("DANGER".equals(status)) return "#F8D7DA";
        if ("WARNING".equals(status)) return "#FFF3CD";
        return "#D4EDDA";
    }

    private static String getStatusTextColor(String status) {
        if ("DANGER".equals(status)) return "#B91C1C";
        if ("WARNING".equals(status)) return "#B45309";
        return "#15803D";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

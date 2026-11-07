import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

    /**
     * Checks whether recipient email has a valid syntax.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        return !trimmed.isEmpty() && EMAIL_PATTERN.matcher(trimmed).matches();
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

        boolean hasEnvCredentials = (envUser != null && !envUser.isEmpty() && envPass != null && !envPass.isEmpty());

        if (hasEnvCredentials) {
            props.setProperty("mail.smtp.host", envHost != null ? envHost : "smtp.gmail.com");
            props.setProperty("mail.smtp.port", envPort != null ? envPort : "587");
            props.setProperty("mail.smtp.auth", envAuth != null ? envAuth : "true");
            props.setProperty("mail.smtp.starttls.enable", envStartTls != null ? envStartTls : "true");
            props.setProperty("mail.smtp.user", envUser);
            props.setProperty("mail.smtp.password", envPass);
            props.setProperty("mail.smtp.from", envUser);
        } else {
            // Environment variables unavailable: attempt to load smtp.properties
            File cfg = new File(CONFIG_FILE);
            if (cfg.exists() && cfg.isFile()) {
                try (FileInputStream fis = new FileInputStream(cfg)) {
                    props.load(fis);
                } catch (IOException ignored) {
                }
            }

            // Defaults if missing in file
            if (!props.containsKey("mail.smtp.host") || props.getProperty("mail.smtp.host").trim().isEmpty()) {
                props.setProperty("mail.smtp.host", "smtp.gmail.com");
            }
            if (!props.containsKey("mail.smtp.port") || props.getProperty("mail.smtp.port").trim().isEmpty()) {
                props.setProperty("mail.smtp.port", "587");
            }
            if (!props.containsKey("mail.smtp.auth") || props.getProperty("mail.smtp.auth").trim().isEmpty()) {
                props.setProperty("mail.smtp.auth", "true");
            }
            if (!props.containsKey("mail.smtp.starttls.enable") || props.getProperty("mail.smtp.starttls.enable").trim().isEmpty()) {
                props.setProperty("mail.smtp.starttls.enable", "true");
            }

            String fileUser = props.getProperty("mail.smtp.user");
            if (fileUser != null && !fileUser.trim().isEmpty()) {
                if (!props.containsKey("mail.smtp.from") || props.getProperty("mail.smtp.from").trim().isEmpty()) {
                    props.setProperty("mail.smtp.from", fileUser.trim());
                }
            }
        }

        // Standard connection timeouts
        props.putIfAbsent("mail.smtp.connectiontimeout", "10000");
        props.putIfAbsent("mail.smtp.timeout", "15000");

        return props;
    }

    private static String getEnv(String name, String fallback) {
        String val = System.getenv(name);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    /**
     * Checks if minimum required SMTP configuration is present.
     */
    public static boolean isConfigured() {
        Properties p = loadSmtpProperties();
        String host = p.getProperty("mail.smtp.host");
        String user = p.getProperty("mail.smtp.user");
        String pass = p.getProperty("mail.smtp.password");

        return host != null && !host.trim().isEmpty()
                && user != null && !user.trim().isEmpty()
                && pass != null && !pass.trim().isEmpty();
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

        if (!isValidEmail(actualEmail)) {
            throw new IllegalArgumentException("Invalid recipient email address: '" + actualEmail + "'. Please verify the student's email.");
        }

        if (pdfAttachment == null || !pdfAttachment.exists()) {
            throw new IOException("PDF report attachment file is missing or was not generated.");
        }

        Properties config = loadSmtpProperties();
        final String username = config.getProperty("mail.smtp.user");
        final String password = config.getProperty("mail.smtp.password");
        final String from = config.getProperty("mail.smtp.from", username);

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new MessagingException("SMTP configuration is incomplete. Please configure 'smtp.properties' or environment variables with valid username and password.");
        }

        // Authenticator
        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        Session session = Session.getInstance(config, auth);

        // Build MimeMessage
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(actualEmail.trim()));

        // Required Subject: Academic Performance Report - [Student Name] - [Roll Number]
        message.setSubject("Academic Performance Report - " + actualName + " - " + rollNo, "UTF-8");

        // Required Body
        String body = "Dear " + actualName + ",\n\n"
                + "Please find attached your latest academic performance report generated by the Student Management System.\n\n"
                + "The report contains your personal academic details, enrolled courses, course-wise attendance, examination marks, grades, and overall academic performance.\n\n"
                + "Please review your attendance status carefully. If your attendance is below the required threshold, kindly take the necessary steps to improve it.\n\n"
                + "Regards,\n"
                + "Student Management System";

        // Multipart container
        Multipart multipart = new MimeMultipart();

        // 1. Text Part
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body);
        multipart.addBodyPart(textPart);

        // 2. Attachment Part
        MimeBodyPart attachmentPart = new MimeBodyPart();
        FileDataSource source = new FileDataSource(pdfAttachment);
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName("Academic_Report_" + rollNo + ".pdf");
        multipart.addBodyPart(attachmentPart);

        message.setContent(multipart);

        // Send via Transport
        Transport.send(message);
    }
}

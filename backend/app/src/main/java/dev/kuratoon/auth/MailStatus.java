package dev.kuratoon.auth;

import dev.kuratoon.config.KuratoonProperties;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * SMTP from {@code SMTP_*} (docs/spec/04-auth-users.md "Registration and password reset"). The
 * connection is verified once at startup; password reset is available only if that check passed.
 */
@Component
public class MailStatus {

    private static final Logger log = LoggerFactory.getLogger(MailStatus.class);
    private static final String TIMEOUT_MS = "5000";

    private final KuratoonProperties.Smtp smtp;
    private final JavaMailSenderImpl sender;
    private volatile boolean ok;
    private volatile String error;

    public MailStatus(KuratoonProperties properties) {
        this.smtp = properties.smtp();
        this.sender = smtp.configured() ? sender(smtp) : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (sender == null) {
            ok = false;
            error = null;
            log.info("SMTP not configured; password reset by email is disabled.");
            return;
        }
        try {
            sender.testConnection();
            ok = true;
            error = null;
            log.info("SMTP connection to {}:{} verified; password reset is enabled.", smtp.host(), smtp.port());
        } catch (Exception e) {
            ok = false;
            error = e.getClass().getSimpleName();
            log.warn("SMTP connection to {}:{} failed ({}); password reset is disabled.",
                    smtp.host(), smtp.port(), e.getMessage());
        }
    }

    public boolean configured() {
        return smtp.configured();
    }

    /** True when SMTP is configured and the startup connection check succeeded. */
    public boolean available() {
        return ok;
    }

    /** Exception type of the failed startup check, or null. */
    public String error() {
        return error;
    }

    public void send(String to, String subject, String text) throws MailException {
        if (!ok) {
            throw new IllegalStateException("Mail is not available");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    private static JavaMailSenderImpl sender(KuratoonProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        boolean auth = smtp.username() != null && !smtp.username().isBlank();
        if (auth) {
            sender.setUsername(smtp.username());
            sender.setPassword(smtp.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(smtp.tls()));
        props.put("mail.smtp.starttls.required", Boolean.toString(smtp.tls()));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }
}

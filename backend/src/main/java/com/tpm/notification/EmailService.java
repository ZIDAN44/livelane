package com.tpm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.tpm.config.AppProperties;

/**
 * Sends HTML e-mail through SMTP when {@code app.mail.enabled=true}; otherwise just logs it,
 * so the app runs anywhere without mail credentials (locally, Mailpit catches everything).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public void send(String to, String subject, String message, String link) {
        var sender = mailSender.getIfAvailable();
        if (!props.mail().enabled() || sender == null) {
            log.info("[email disabled] to={} subject=\"{}\"", to, subject);
            return;
        }
        try {
            var mime = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(render(message, link), true);
            sender.send(mime);
            log.info("Sent email to={} subject=\"{}\"", to, subject);
        } catch (Exception e) {
            // Notifications are best effort: never fail the business operation because of e-mail.
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    String render(String message, String link) {
        var url = props.frontendUrl() + (link == null ? "" : link);
        return """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:auto;padding:24px">
                  <div style="font-weight:700;font-size:18px;color:#4f46e5;margin-bottom:16px">LiveLane</div>
                  <p style="font-size:15px;color:#111827;line-height:1.5">%s</p>
                  <a href="%s" style="display:inline-block;margin-top:12px;padding:10px 16px;background:#4f46e5;color:#fff;
                     border-radius:8px;text-decoration:none;font-size:14px">Open in LiveLane</a>
                  <p style="font-size:12px;color:#6b7280;margin-top:24px">
                    You can turn off e-mail notifications in your profile settings.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(message), HtmlUtils.htmlEscape(url));
    }
}

package ca.curbcutting.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        if (mailSender == null) {
            log.warn("No mail sender configured (spring.mail.* not set) — password reset code for {} is: {}", toEmail, code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Your Curbcutting password reset code");
            message.setText("Your password reset code is: " + code
                    + "\n\nThis code expires in 15 minutes. If you didn't request this, you can ignore this email.");
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send password reset email to {} — code is: {} ({})", toEmail, code, e.getMessage());
        }
    }
}

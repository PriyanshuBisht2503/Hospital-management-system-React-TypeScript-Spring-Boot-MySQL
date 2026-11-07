package com.hms.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final Environment environment;

    @Value("${app.mail.from:noreply@example.com}")
    private String mailFrom;

    public EmailService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Sends OTP to patient. Never logs raw OTP outside dev profile.
     */
    public void sendOtp(String target, String rawOtp) {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            log.info("[DEV ONLY] Patient OTP for {}: {}", target, rawOtp);
        }

        if (target != null && target.contains("@") && mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(target);
                message.setSubject("Your CareHMS Verification Code");
                message.setText("Your single-use verification code is: " + rawOtp + "\n\nThis code expires in 5 minutes. If you did not request this, please ignore this email.");
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Failed to deliver OTP email to {}: {}", target, e.getMessage());
            }
        }
    }

    /**
     * Sends password reset email. Never logs raw token outside dev profile.
     */
    public void sendPasswordReset(String to, String rawToken) {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            log.info("[DEV ONLY] Password reset token for {}: {}", to, rawToken);
        }

        if (to != null && to.contains("@") && mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(to);
                message.setSubject("CareHMS - Password Reset Request");
                message.setText("You requested a password reset. Use the following token to reset your password:\n\n" + rawToken + "\n\nThis token expires in 15 minutes.");
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Failed to deliver password reset email to {}: {}", to, e.getMessage());
            }
        }
    }

    public void sendEmail(String to, String subject, String body) {
        if (to != null && to.contains("@") && mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        }
    }
}

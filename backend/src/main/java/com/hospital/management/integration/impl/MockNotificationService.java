package com.hospital.management.integration.impl;

import com.hospital.management.integration.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MockNotificationService implements NotificationService {
    @Override
    public void sendEmail(String toEmail, String title, String body) {
        log.info("Mock email sent to: {} with Title: '{}'", toEmail, title);
    }

    @Override
    public void sendSms(String toPhone, String body) {
        log.info("Mock SMS sent to: {} with Content: '{}'", toPhone, body);
    }
}

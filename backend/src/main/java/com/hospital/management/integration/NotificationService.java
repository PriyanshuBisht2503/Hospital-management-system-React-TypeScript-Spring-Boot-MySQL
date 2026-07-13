package com.hospital.management.integration;

public interface NotificationService {
    void sendEmail(String toEmail, String title, String body);
    void sendSms(String toPhone, String body);
}

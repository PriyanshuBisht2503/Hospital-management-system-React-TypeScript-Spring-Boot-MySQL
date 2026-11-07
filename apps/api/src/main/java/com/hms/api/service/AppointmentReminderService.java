package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.Notification;
import com.hms.api.domain.NotificationChannel;
import com.hms.api.domain.NotificationStatus;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AppointmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final Clock clock;

    public AppointmentReminderService(
            AppointmentRepository appointmentRepository,
            NotificationRepository notificationRepository,
            EmailService emailService,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.appointment.reminder-cron:0 * * * * *}")
    @Transactional
    public void sendUpcomingReminders() {
        process24hReminders();
        process2hReminders();
    }

    @Transactional
    public void process24hReminders() {
        Instant now = clock.instant();
        Instant windowStart = now.plus(2, ChronoUnit.HOURS);
        Instant windowEnd = now.plus(24, ChronoUnit.HOURS);

        List<Appointment> due = appointmentRepository.findDueFor24hReminder(windowStart, windowEnd);

        for (Appointment a : due) {
            // Rule: Skip 24h reminder if booked < 24h ahead
            Instant bookedAt = a.getCreatedAt() != null ? a.getCreatedAt() : now;
            Instant twentyFourHoursBeforeStart = a.getStartTime().minus(24, ChronoUnit.HOURS);
            if (bookedAt.isAfter(twentyFourHoursBeforeStart)) {
                log.info("Skipping 24h reminder for appointment {} (booked < 24h before start time)", a.getId());
                appointmentRepository.claimReminder24h(a.getId());
                continue;
            }

            int claimed = appointmentRepository.claimReminder24h(a.getId());
            if (claimed > 0) {
                String recipient = a.getPatient().getEmail();
                if (recipient != null && !recipient.isBlank()) {
                    String timeStr = FORMATTER.format(a.getStartTime().atZone(ZoneId.of("Asia/Kolkata")));
                    String subject = "Reminder: Your appointment tomorrow with " + a.getDoctor().getUser().getFullName();
                    String body = String.format(
                            "Dear %s,\n\nThis is a reminder for your upcoming appointment with %s at %s scheduled for %s.",
                            a.getPatient().getFullName(),
                            a.getDoctor().getUser().getFullName(),
                            a.getBranch().getName(),
                            timeStr
                    );
                    dispatchNotification(a, recipient, subject, body);
                }
            }
        }
    }

    @Transactional
    public void process2hReminders() {
        Instant now = clock.instant();
        Instant windowStart = now;
        Instant windowEnd = now.plus(2, ChronoUnit.HOURS);

        List<Appointment> due = appointmentRepository.findDueFor2hReminder(windowStart, windowEnd);

        for (Appointment a : due) {
            int claimed = appointmentRepository.claimReminder2h(a.getId());
            if (claimed > 0) {
                String recipient = a.getPatient().getEmail();
                if (recipient != null && !recipient.isBlank()) {
                    String timeStr = FORMATTER.format(a.getStartTime().atZone(ZoneId.of("Asia/Kolkata")));
                    String subject = "Reminder: Your appointment in 2 hours with " + a.getDoctor().getUser().getFullName();
                    String body = String.format(
                            "Dear %s,\n\nYour appointment with %s at %s starts in 2 hours at %s. Please arrive 10 minutes early.",
                            a.getPatient().getFullName(),
                            a.getDoctor().getUser().getFullName(),
                            a.getBranch().getName(),
                            timeStr
                    );
                    dispatchNotification(a, recipient, subject, body);
                }
            }
        }
    }

    private void dispatchNotification(Appointment a, String recipient, String subject, String body) {
        Notification notification = new Notification(
                a.getPatient().getUser(),
                a.getPatient(),
                NotificationChannel.EMAIL,
                subject,
                body
        );
        notification = notificationRepository.save(notification);

        try {
            emailService.sendEmail(recipient, subject, body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(clock.instant());
        } catch (Exception e) {
            log.warn("Failed to deliver reminder email to {}: {}", recipient, e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
        }
        notificationRepository.save(notification);
    }
}

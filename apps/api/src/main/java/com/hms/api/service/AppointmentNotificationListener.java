package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.Notification;
import com.hms.api.domain.NotificationChannel;
import com.hms.api.domain.NotificationStatus;
import com.hms.api.event.AppointmentBookedEvent;
import com.hms.api.event.AppointmentCancelledEvent;
import com.hms.api.event.AppointmentRescheduledEvent;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final Clock clock;

    public AppointmentNotificationListener(
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

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        appointmentRepository.findById(event.appointmentId()).ifPresent(appt -> {
            String recipient = appt.getPatient().getEmail();
            if (recipient == null || recipient.isBlank()) return;

            String timeStr = FORMATTER.format(appt.getStartTime().atZone(ZoneId.of("Asia/Kolkata")));
            String subject = "Appointment Confirmation: " + appt.getDoctor().getUser().getFullName();
            String body = String.format(
                    "Dear %s,\n\nYour appointment with %s at %s has been scheduled for %s.\n\nThank you for choosing CareHMS.",
                    appt.getPatient().getFullName(),
                    appt.getDoctor().getUser().getFullName(),
                    appt.getBranch().getName(),
                    timeStr
            );

            dispatchNotification(appt, recipient, subject, body);
        });
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        appointmentRepository.findById(event.appointmentId()).ifPresent(appt -> {
            String recipient = appt.getPatient().getEmail();
            if (recipient == null || recipient.isBlank()) return;

            String timeStr = FORMATTER.format(appt.getStartTime().atZone(ZoneId.of("Asia/Kolkata")));
            String subject = "Appointment Cancelled: " + appt.getDoctor().getUser().getFullName();
            String body = String.format(
                    "Dear %s,\n\nYour appointment scheduled for %s has been cancelled. Reason: %s.\n\nPlease contact us if you need to reschedule.",
                    appt.getPatient().getFullName(),
                    timeStr,
                    appt.getCancelReason() != null ? appt.getCancelReason() : "N/A"
            );

            dispatchNotification(appt, recipient, subject, body);
        });
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentRescheduled(AppointmentRescheduledEvent event) {
        appointmentRepository.findById(event.newAppointmentId()).ifPresent(appt -> {
            String recipient = appt.getPatient().getEmail();
            if (recipient == null || recipient.isBlank()) return;

            String timeStr = FORMATTER.format(appt.getStartTime().atZone(ZoneId.of("Asia/Kolkata")));
            String subject = "Appointment Rescheduled: " + appt.getDoctor().getUser().getFullName();
            String body = String.format(
                    "Dear %s,\n\nYour appointment has been successfully rescheduled to %s with %s at %s.",
                    appt.getPatient().getFullName(),
                    timeStr,
                    appt.getDoctor().getUser().getFullName(),
                    appt.getBranch().getName()
            );

            dispatchNotification(appt, recipient, subject, body);
        });
    }

    private void dispatchNotification(Appointment appt, String recipient, String subject, String body) {
        Notification notification = new Notification(
                appt.getPatient().getUser(),
                appt.getPatient(),
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
            log.warn("Failed to deliver notification email to {}: {}", recipient, e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
        }
        notificationRepository.save(notification);
    }
}

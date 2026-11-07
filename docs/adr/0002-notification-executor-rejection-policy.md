# ADR 0002: Notification Executor Rejection Policy (CallerRunsPolicy)

## Status
Accepted

## Context
In Phase 2, appointment lifecycle events (`AppointmentBookedEvent`, `AppointmentCancelledEvent`, `AppointmentRescheduledEvent`) trigger patient notifications. These notifications are delivered asynchronously via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` listeners executed on a dedicated Spring `ThreadPoolTaskExecutor` bean (`notificationTaskExecutor`).

Under heavy appointment booking spikes or transient mail provider delays, the notification task queue could become saturated. If the task executor defaults to `AbortPolicy` (which throws `RejectedExecutionException`), or if the exception propagates unhandled, it could turn an otherwise successfully committed transaction into an HTTP 500 error for the client, or fail the request despite the appointment having already been committed to the database.

## Decision
1. **Executor Configuration:**
   Configure `notificationTaskExecutor` with `ThreadPoolExecutor.CallerRunsPolicy`:
   ```java
   ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
   executor.setCorePoolSize(4);
   executor.setMaxPoolSize(10);
   executor.setQueueCapacity(100);
   executor.setThreadNamePrefix("notification-");
   executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
   executor.initialize();
   ```

2. **Guarantees:**
   - **No HTTP 500 on Queue Saturation:** When the queue reaches capacity (100 pending notifications) and all 10 worker threads are active, `CallerRunsPolicy` forces the task to execute synchronously on the submitting thread after transaction commit.
   - **Transaction Integrity:** Because event listeners execute in `AFTER_COMMIT` with `Propagation.REQUIRES_NEW`, appointment records remain fully committed and intact regardless of email delivery speed or queue congestion.
   - **Failure Resilience:** Even when email delivery fails (e.g. SMTP down), the notification record status is marked `FAILED` without failing the caller or throwing unhandled exceptions.


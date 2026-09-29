package com.example.interviewtest.message;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private static final int DAILY_MESSAGE_LIMIT = 10;

    private final EntityManager entityManager;

    public MessageController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> send(@RequestBody SendMessageRequest request) {
        if (request.sender() == null || request.sender().isBlank()
                || request.recipient() == null || request.recipient().isBlank()
                || request.body() == null || request.body().isBlank()) {
            return ResponseEntity.badRequest().body("Sender, recipient and body are required");
        }

        String sender = request.sender().trim().toLowerCase(Locale.ROOT);
        String recipient = request.recipient().trim().toLowerCase(Locale.ROOT);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Long messagesSentToday = entityManager.createQuery(
                        "select count(m) from MessageRow m where m.sender = :sender and m.createdAt >= :startOfDay",
                        Long.class)
                .setParameter("sender", sender)
                .setParameter("startOfDay", startOfDay)
                .getSingleResult();
        if (messagesSentToday >= DAILY_MESSAGE_LIMIT) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Daily message limit exceeded");
        }

        MessageRow message = new MessageRow();
        message.sender = sender;
        message.recipient = recipient;
        message.body = request.body().trim();
        message.priority = request.urgent() ? "HIGH" : "NORMAL";
        message.status = recipient.endsWith("@company.test") ? "INTERNAL" : "QUEUED";
        message.createdAt = Instant.now();
        entityManager.persist(message);

        createDeliveryRecord(message);

        return ResponseEntity.ok(new MessageResponse(
                message.id, message.status, message.priority, message.createdAt));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        MessageRow message = entityManager.find(MessageRow.class, id);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new MessageResponse(
                message.id, message.status, message.priority, message.createdAt));
    }

    @Transactional
    public void createDeliveryRecord(MessageRow message) {
        DeliveryRow delivery = new DeliveryRow();
        delivery.messageId = message.id;
        delivery.recipient = message.recipient;
        delivery.status = message.status.equals("INTERNAL") ? "DELIVERED" : "PENDING";
        delivery.createdAt = Instant.now();
        entityManager.persist(delivery);
    }

    public record SendMessageRequest(String sender, String recipient, String body, boolean urgent) {}

    public record MessageResponse(Long id, String status, String priority, Instant createdAt) {}

}

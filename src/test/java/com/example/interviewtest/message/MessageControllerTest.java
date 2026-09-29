package com.example.interviewtest.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MessageControllerTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final TypedQuery<Long> countQuery = mock(TypedQuery.class);
    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(entityManager);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
    }

    @Test
    void rejectsMessageWhenSenderHasReachedDailyLimit() {
        when(countQuery.getSingleResult()).thenReturn(10L);

        ResponseEntity<?> response = controller.send(
                new MessageController.SendMessageRequest(
                        "sender@example.com", "recipient@example.com", "Hello", false));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("Daily message limit exceeded", response.getBody());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void createsMessageAndDeliveryWhenSenderIsBelowDailyLimit() {
        when(countQuery.getSingleResult()).thenReturn(9L);

        ResponseEntity<?> response = controller.send(
                new MessageController.SendMessageRequest(
                        "sender@example.com", "person@company.test", "Hello", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        MessageController.MessageResponse message =
                assertInstanceOf(MessageController.MessageResponse.class, response.getBody());
        assertEquals("INTERNAL", message.status());
        assertEquals("HIGH", message.priority());
        verify(entityManager, times(2)).persist(any());
    }
}

package com.lumira.review.api;

/**
 * Review-owned notification boundary. The review module records invitation
 * state; a message adapter is responsible for the actual mail delivery.
 */
public interface ReviewNotificationPort {

    void sendReviewInvitation(String recipientEmail, String subject, String content);
}

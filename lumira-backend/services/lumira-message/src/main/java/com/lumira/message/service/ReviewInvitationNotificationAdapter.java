package com.lumira.message.service;

import com.lumira.review.api.ReviewNotificationPort;

/** Message-owned adapter for review invitation mail delivery. */
public class ReviewInvitationNotificationAdapter implements ReviewNotificationPort {

    private final SmtpNotificationMailService smtpNotificationMailService;

    public ReviewInvitationNotificationAdapter(SmtpNotificationMailService smtpNotificationMailService) {
        this.smtpNotificationMailService = smtpNotificationMailService;
    }

    @Override
    public void sendReviewInvitation(String recipientEmail, String subject, String content) {
        smtpNotificationMailService.send(recipientEmail, subject, content);
    }
}

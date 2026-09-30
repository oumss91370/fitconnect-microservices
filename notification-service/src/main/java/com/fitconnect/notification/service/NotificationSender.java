package com.fitconnect.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Passerelle email/SMS simulée : l'envoi est tracé dans les logs.
 * Un destinataire contenant "fail" (configurable) simule une panne du fournisseur.
 */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final String failingKeyword;

    public NotificationSender(@Value("${fitconnect.notification.failing-recipient-keyword:fail}") String failingKeyword) {
        this.failingKeyword = failingKeyword;
    }

    public void send(String to, String subject, String content) {
        if (!failingKeyword.isBlank() && to.toLowerCase().contains(failingKeyword)) {
            throw new IllegalStateException("Fournisseur email indisponible pour " + to + " (simulation)");
        }
        log.info("[EMAIL] à={} | sujet=\"{}\" | {}", to, subject, content);
    }
}

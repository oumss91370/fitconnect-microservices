package com.fitconnect.notification.dto;

import com.fitconnect.notification.model.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(
        @NotNull(message = "userId est obligatoire") Long userId,
        @NotBlank(message = "l'email est obligatoire") @Email(message = "email invalide") String email,
        @NotNull(message = "le type est obligatoire") NotificationType type,
        @NotBlank(message = "le sujet est obligatoire") String subject,
        @NotBlank(message = "le contenu est obligatoire") String content) {
}

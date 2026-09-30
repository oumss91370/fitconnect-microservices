package com.fitconnect.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", contextId = "notificationClient", path = "/api/notifications",
        fallbackFactory = NotificationClientFallbackFactory.class, primary = false)
public interface NotificationClient {

    @PostMapping
    NotificationDto send(@RequestBody NotificationRequestDto request);
}

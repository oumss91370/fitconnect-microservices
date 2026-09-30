package com.fitconnect.booking.support;

import com.fitconnect.booking.client.NotificationClient;
import com.fitconnect.booking.client.NotificationDto;
import com.fitconnect.booking.client.NotificationRequestDto;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Double de test de notification-service : enregistre les notifications envoyées. */
public class FakeNotificationClient implements NotificationClient {

    public final List<NotificationRequestDto> sent = new CopyOnWriteArrayList<>();

    public List<String> types() {
        return sent.stream().map(NotificationRequestDto::type).toList();
    }

    @Override
    public NotificationDto send(NotificationRequestDto r) {
        sent.add(r);
        return new NotificationDto((long) sent.size(), r.userId(), r.email(), r.type(), r.subject(), "SENT");
    }
}

package com.fitconnect.booking.client;

import com.fitconnect.booking.exception.BookingConflictException;
import com.fitconnect.booking.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    private static final Logger log = LoggerFactory.getLogger(PaymentClientFallbackFactory.class);

    @Override
    public PaymentClient create(Throwable cause) {
        return new PaymentClient() {
            @Override
            public PaymentDto processPayment(PaymentRequestDto request) {
                throw translate(cause, "traitement du paiement");
            }

            @Override
            public PaymentDto getPaymentByBooking(Long bookingId) {
                throw translate(cause, "lecture du paiement");
            }

            @Override
            public PaymentDto refund(Long paymentId) {
                throw translate(cause, "remboursement");
            }
        };
    }

    private RuntimeException translate(Throwable cause, String operation) {
        int status = RemoteErrors.status(cause);
        if (status >= 400 && status < 500) {
            return new BookingConflictException(RemoteErrors.remoteMessage(cause,
                    "payment-service a refusé l'opération (" + operation + ")"));
        }
        log.error("payment-service indisponible ({}) : {}", operation, RemoteErrors.technicalReason(cause));
        return new ServiceUnavailableException("payment-service indisponible (" + RemoteErrors.technicalReason(cause)
                + ") : " + operation + " impossible, réessayez plus tard");
    }
}

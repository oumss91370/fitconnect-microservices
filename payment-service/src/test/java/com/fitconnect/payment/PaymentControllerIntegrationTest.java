package com.fitconnect.payment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PaymentControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String pay(long bookingId, String amount, String method, String card) throws Exception {
        String cardJson = card == null ? "" : ",\"cardLastFour\":\"" + card + "\"";
        return mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingId":%d,"bookingReference":"BK-TEST%d","userId":1,"amount":%s,"paymentMethod":"%s"%s}
                        """.formatted(bookingId, bookingId, amount, method, cardJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void paymentBelow100_isAccepted_thenRefunded_once() throws Exception {
        String body = pay(1, "30.00", "CREDIT_CARD", "1234");
        Integer id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/payments/booking/1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentReference", matchesPattern("PAY-[A-Z0-9]{5}")))
                .andExpect(jsonPath("$.transactionId", matchesPattern("txn_[a-f0-9]{12}")));
        mvc.perform(post("/api/payments/{id}/refund", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(post("/api/payments/{id}/refund", id)).andExpect(status().isConflict());
    }

    @Test
    void paymentOf100OrMore_isRefused() throws Exception {
        String body = pay(2, "100.00", "PAYPAL", null);
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        Integer id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/payments/{id}/refund", id)).andExpect(status().isConflict());
    }

    @Test
    void secondPaymentForPaidBooking_isRejected_andCardNeedsLastFour() throws Exception {
        pay(3, "20.00", "STRIPE", null);
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingId":3,"userId":1,"amount":20.00,"paymentMethod":"STRIPE"}"""))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingId":4,"userId":1,"amount":20.00,"paymentMethod":"DEBIT_CARD"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payments/user/1")).andExpect(jsonPath("$.length()").value(1));
    }
}

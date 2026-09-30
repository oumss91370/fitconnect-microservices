package com.fitconnect.notification;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NotificationControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String send(String email) throws Exception {
        return mvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId":1,"email":"%s","type":"BOOKING_CONFIRMATION","subject":"Réservation","content":"Payez avant 18h"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void notificationIsSent_andListedForUser() throws Exception {
        send("john@example.com");
        mvc.perform(get("/api/notifications/user/1"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].sentDate").exists());
        mvc.perform(get("/api/notifications/pending")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void failedNotification_isPending_andCanBeRetried() throws Exception {
        Integer id = JsonPath.read(send("fail@example.com"), "$.id");
        mvc.perform(get("/api/notifications/pending"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].attempts").value(1));
        mvc.perform(patch("/api/notifications/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(2));
    }

    @Test
    void invalidNotification_returns400_andRetryOfSentOne_returns409() throws Exception {
        mvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"email\":\"pas-un-email\",\"type\":\"BOOKING_REMINDER\"}"))
                .andExpect(status().isBadRequest());
        Integer id = JsonPath.read(send("ok@example.com"), "$.id");
        mvc.perform(patch("/api/notifications/{id}/retry", id)).andExpect(status().isConflict());
    }
}

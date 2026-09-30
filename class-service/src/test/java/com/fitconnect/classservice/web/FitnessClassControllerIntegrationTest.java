package com.fitconnect.classservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FitnessClassControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String body(String name, String category, String level, int duration, int max, String price, LocalDateTime date) {
        return """
                {"name":"%s","description":"Cours de test","instructor":"Marie Dupont","gymLocation":"Paris 11",
                 "category":"%s","level":"%s","durationMinutes":%d,"maxParticipants":%d,"price":%s,"dateTime":"%s"}
                """.formatted(name, category, level, duration, max, price, date.truncatedTo(ChronoUnit.SECONDS));
    }

    private void create(String name, String category, String level, LocalDateTime date) throws Exception {
        mvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, category, level, 60, 10, "15.00", date)))
                .andExpect(status().isCreated());
    }

    @Test
    void createClass_thenGet() throws Exception {
        mvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Yoga Vinyasa", "YOGA", "INTERMEDIATE", 60, 10, "15.00", LocalDateTime.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/classes/1")))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.currentParticipants").value(0))
                .andExpect(jsonPath("$.availableSpots").value(10));
        mvc.perform(get("/api/classes/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Yoga Vinyasa"));
        mvc.perform(get("/api/classes/99")).andExpect(status().isNotFound());
    }

    @Test
    void createClass_withInvalidData_returns400WithDetails() throws Exception {
        mvc.perform(post("/api/classes").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Yo", "YOGA", "BEGINNER", 50, 40, "3.00", LocalDateTime.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.durationMinutes").exists())
                .andExpect(jsonPath("$.details.maxParticipants").exists())
                .andExpect(jsonPath("$.details.price").exists())
                .andExpect(jsonPath("$.details.dateTime").exists());
    }

    @Test
    void listClasses_withFiltersAndPagination() throws Exception {
        LocalDateTime d = LocalDateTime.now().plusDays(5);
        create("Yoga doux", "YOGA", "BEGINNER", d);
        create("Yoga power", "YOGA", "INTERMEDIATE", d.plusDays(1));
        create("Zumba party", "ZUMBA", "BEGINNER", d.plusDays(2));

        mvc.perform(get("/api/classes").param("category", "YOGA").param("level", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Yoga power"));

        mvc.perform(get("/api/classes").param("page", "0").param("size", "2").param("sort", "dateTime,desc"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Zumba party"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/classes/search").param("location", "paris").param("instructor", "marie")
                        .param("date", d.toLocalDate().toString()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Yoga doux"));
    }

    @Test
    void increment_returns409_whenNotEnoughSpots() throws Exception {
        create("Spinning", "SPINNING", "ADVANCED", LocalDateTime.now().plusDays(1));
        mvc.perform(patch("/api/classes/1/increment").param("spots", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentParticipants").value(9));
        mvc.perform(patch("/api/classes/1/increment").param("spots", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Plus de places disponibles")));
        mvc.perform(patch("/api/classes/1/decrement").param("spots", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentParticipants").value(5));
    }

    @Test
    void delete_cancelsClassWithParticipants_andDeletesEmptyOne() throws Exception {
        create("Pilates", "PILATES", "BEGINNER", LocalDateTime.now().plusDays(1));
        create("Boxe", "BOXING", "BEGINNER", LocalDateTime.now().plusDays(1));
        mvc.perform(patch("/api/classes/1/increment").param("spots", "2")).andExpect(status().isOk());
        mvc.perform(delete("/api/classes/1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/classes/2")).andExpect(status().isNoContent());
        mvc.perform(get("/api/classes/2")).andExpect(status().isNotFound());
    }
}

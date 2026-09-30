package com.fitconnect.classservice.service;

import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.exception.ConcurrentUpdateException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.FitnessClass;
import com.fitconnect.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests d'intégration du verrouillage optimiste (@Version) avec une vraie base H2. */
@SpringBootTest
class OptimisticLockingIntegrationTest {

    @Autowired
    FitnessClassService service;

    @Autowired
    FitnessClassRepository repository;

    private FitnessClassResponse createClass(int maxParticipants) {
        return service.create(new FitnessClassRequest("CrossFit WOD", "Entraînement intense", "Karim",
                "Paris 12", ClassCategory.CROSSFIT, ClassLevel.ADVANCED, 45, maxParticipants, 0,
                new BigDecimal("20.00"), LocalDateTime.now().plusDays(2), null));
    }

    @Test
    void secondUpdateWithStaleVersion_isRejected() {
        Long id = createClass(10).id();
        FitnessClass copyA = repository.findById(id).orElseThrow();   // version 0
        FitnessClass copyB = repository.findById(id).orElseThrow();   // version 0 (copie périmée)

        copyA.incrementParticipants(2);
        repository.saveAndFlush(copyA);                               // UPDATE ... WHERE version = 0 -> version 1

        copyB.incrementParticipants(2);
        assertThatThrownBy(() -> repository.saveAndFlush(copyB))      // UPDATE ... WHERE version = 0 -> 0 ligne
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        FitnessClass reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(2);  // pas de mise à jour perdue
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void concurrentBookings_neverExceedCapacity() throws Exception {
        // 12 réservations simultanées de 5 places sur un cours de 30 places : 6 seulement doivent passer
        Long id = createClass(30).id();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger noSpots = new AtomicInteger();
        AtomicInteger otherConflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.incrementParticipants(id, 5);
                    success.incrementAndGet();
                } catch (NoSpotsAvailableException e) {
                    noSpots.incrementAndGet();
                } catch (ConcurrentUpdateException e) {
                    otherConflicts.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        FitnessClass result = repository.findById(id).orElseThrow();
        assertThat(result.getCurrentParticipants()).isEqualTo(5 * success.get());
        assertThat(result.getCurrentParticipants()).isLessThanOrEqualTo(30);
        assertThat(success.get()).isEqualTo(6);
        assertThat(noSpots.get() + otherConflicts.get()).isEqualTo(threads - 6);
    }
}

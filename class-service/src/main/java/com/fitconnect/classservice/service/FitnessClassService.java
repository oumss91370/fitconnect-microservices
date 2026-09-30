package com.fitconnect.classservice.service;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.exception.ConcurrentUpdateException;
import com.fitconnect.classservice.exception.InvalidParticipantsException;
import com.fitconnect.classservice.exception.ResourceNotFoundException;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.model.FitnessClass;
import com.fitconnect.classservice.repository.FitnessClassRepository;
import com.fitconnect.classservice.repository.FitnessClassSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

@Service
public class FitnessClassService {

    private static final Logger log = LoggerFactory.getLogger(FitnessClassService.class);

    private final FitnessClassRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;

    public FitnessClassService(FitnessClassRepository repository, TransactionTemplate transactionTemplate,
                               @Value("${fitconnect.class.optimistic-lock-retries:3}") int maxAttempts) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional(readOnly = true)
    public Page<FitnessClassResponse> search(ClassSearchCriteria criteria, Pageable pageable) {
        return repository.findAll(FitnessClassSpecifications.matching(criteria), pageable)
                .map(FitnessClassResponse::from);
    }

    @Transactional(readOnly = true)
    public FitnessClassResponse findById(Long id) {
        return FitnessClassResponse.from(getOrThrow(id));
    }

    @Transactional
    public FitnessClassResponse create(FitnessClassRequest r) {
        FitnessClass c = new FitnessClass();
        apply(c, r);
        c.setCurrentParticipants(Optional.ofNullable(r.currentParticipants()).orElse(0));
        c.setStatus(Optional.ofNullable(r.status()).orElse(ClassStatus.SCHEDULED));
        return FitnessClassResponse.from(repository.save(c));
    }

    @Transactional
    public FitnessClassResponse update(Long id, FitnessClassRequest r) {
        FitnessClass c = getOrThrow(id);
        if (r.maxParticipants() < c.getCurrentParticipants()) {
            throw new InvalidParticipantsException("maxParticipants (" + r.maxParticipants()
                    + ") ne peut pas être inférieur aux " + c.getCurrentParticipants() + " participant(s) déjà inscrit(s)");
        }
        apply(c, r);
        if (r.currentParticipants() != null) c.setCurrentParticipants(r.currentParticipants());
        if (r.status() != null) c.setStatus(r.status());
        return FitnessClassResponse.from(repository.saveAndFlush(c));
    }

    /**
     * Sans participant : suppression physique (retourne vide).
     * Avec participants : le cours passe en CANCELLED pour garder l'historique des réservations.
     */
    @Transactional
    public Optional<FitnessClassResponse> deleteOrCancel(Long id) {
        FitnessClass c = getOrThrow(id);
        if (c.getCurrentParticipants() == 0) {
            repository.delete(c);
            return Optional.empty();
        }
        c.setStatus(ClassStatus.CANCELLED);
        return Optional.of(FitnessClassResponse.from(repository.saveAndFlush(c)));
    }

    /** PATCH /increment : réservation de places, protégée par le verrouillage optimiste. */
    public FitnessClassResponse incrementParticipants(Long id, int spots) {
        return updateWithOptimisticRetry(id, c -> c.incrementParticipants(spots), "increment +" + spots);
    }

    /** PATCH /decrement : libération de places (annulation, expiration, compensation). */
    public FitnessClassResponse decrementParticipants(Long id, int spots) {
        return updateWithOptimisticRetry(id, c -> c.decrementParticipants(spots), "decrement -" + spots);
    }

    /**
     * Chaque tentative relit le cours dans une nouvelle transaction et applique la règle métier.
     * Si un autre appel a modifié le cours entre-temps, l'UPDATE ... WHERE version = ? ne touche
     * aucune ligne : Hibernate lève une OptimisticLockException et on recommence avec l'état à jour.
     * La vérification "places restantes" est donc toujours faite sur la dernière version du cours.
     */
    private FitnessClassResponse updateWithOptimisticRetry(Long id, Consumer<FitnessClass> change, String label) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    FitnessClass c = getOrThrow(id);
                    change.accept(c);
                    return FitnessClassResponse.from(repository.saveAndFlush(c));
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Conflit de version sur le cours {} ({}), tentative {}/{}", id, label, attempt, maxAttempts);
                if (attempt >= maxAttempts) {
                    throw new ConcurrentUpdateException("Le cours " + id
                            + " est modifié par d'autres réservations en parallèle, veuillez réessayer");
                }
            }
        }
    }

    private FitnessClass getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cours " + id + " introuvable"));
    }

    private static void apply(FitnessClass c, FitnessClassRequest r) {
        c.setName(r.name());
        c.setDescription(r.description());
        c.setInstructor(r.instructor());
        c.setGymLocation(r.gymLocation());
        c.setCategory(r.category());
        c.setLevel(r.level());
        c.setDurationMinutes(r.durationMinutes());
        c.setMaxParticipants(r.maxParticipants());
        c.setPrice(r.price());
        c.setDateTime(r.dateTime());
    }
}

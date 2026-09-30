# FitConnect — réservation et paiement de cours de sport en microservices

TP **Efrei M2 DEV2 — Microservices**. La plateforme compte 4 microservices métier, orchestrés par une saga « Reserve Now, Pay Later ». Elle s'appuie sur l'infrastructure Spring Cloud habituelle : Eureka, Config Server et API Gateway.

| Livrable demandé | Où le trouver | État |
|---|---|---|
| Code source | `class-service/`, `booking-service/`, `payment-service/`, `notification-service/` (+ `eureka-server/`, `config-server/`, `api-gateway/`) | ✅ |
| Fichiers de configuration dans config-repo | `config-repo/*.yml` (1 fichier commun + 5 fichiers de service) | ✅ |
| Routes dans api-gateway.yml | `config-repo/api-gateway.yml` (4 routes `lb://`) | ✅ |
| Clients Feign avec Circuit Breaker | `booking-service/.../client/` (3 clients + 3 `FallbackFactory`, Resilience4j) | ✅ |
| Pattern Saga complet | `booking-service/.../service/BookingService.java` (réservation → paiement → confirmation / annulation, avec compensations) | ✅ |
| Verrouillage optimiste | `class-service/.../model/FitnessClass.java` (`@Version`) + nouvelle tentative dans `FitnessClassService` | ✅ |
| Scheduler | `booking-service/.../scheduler/BookingScheduler.java` (expiration toutes les 5 min + rappel 24 h avant) | ✅ |
| Collection Postman complète | `postman/FitConnect.postman_collection.json` (41 requêtes, 69 assertions) | ✅ |
| Tests unitaires et d'intégration | 30 tests, tous verts (`mvnw test`) | ✅ |
| README détaillé | ce fichier | ✅ |
| Docker Compose (bonus) | `docker-compose.yml` (7 conteneurs) | ✅ |

---

## 1. Architecture

```
                                 ┌──────────────────┐
                                 │  eureka-server   │ :8761  annuaire des services
                                 └────────▲─────────┘
                                          │ enregistrement / découverte
   client ──► ┌──────────────┐            │
  (Postman)   │ api-gateway  │ :8080 ─────┼──► lb://class-service         :8091  H2 classdb
              │ 4 routes     │            ├──► lb://booking-service       :8092  H2 bookingdb
              └──────────────┘            ├──► lb://payment-service       :8093  H2 paymentdb
                                          └──► lb://notification-service  :8094  H2 notificationdb

   booking-service = orchestrateur de la saga (OpenFeign + Resilience4j) :
        ├── ClassClient        → class-service         GET /{id}, PATCH /{id}/increment, PATCH /{id}/decrement
        ├── PaymentClient      → payment-service       POST /, GET /booking/{id}, POST /{id}/refund
        └── NotificationClient → notification-service  POST /

   config-server :8888 (profil native) sert config-repo/ à tous les services au démarrage
```

| Service | Port | Rôle | Base |
|---|---|---|---|
| eureka-server | 8761 | Registre des services | — |
| config-server | 8888 | Configuration centralisée (`config-repo/`) | — |
| api-gateway | 8080 | Point d'entrée unique, routage `lb://` via Eureka | — |
| class-service | 8091 | Cours, filtres et pagination, **verrouillage optimiste** | H2 `classdb` |
| booking-service | 8092 | Réservations, **orchestration de la saga**, **scheduler** | H2 `bookingdb` |
| payment-service | 8093 | Paiements simulés, remboursements | H2 `paymentdb` |
| notification-service | 8094 | Emails / SMS simulés (tracés dans les logs), relance | H2 `notificationdb` |

**Stack :** Java 21 · Spring Boot 3.5.16 · Spring Cloud 2025.0.3 (Netflix Eureka, Config Server en profil native, Gateway WebFlux, OpenFeign avec client Apache HC5, CircuitBreaker Resilience4j) · Spring Data JPA · H2 · Bean Validation · Lombok · JUnit 5, Mockito, MockMvc · Maven multi-modules.

> Je n'ai pas retrouvé l'infrastructure des modules précédents (eureka-server, config-server, api-gateway), je l'ai donc recréée dans ce dépôt pour que le projet démarre de bout en bout. Pour la brancher sur une infrastructure existante, il suffit de copier les 5 fichiers de `config-repo/` dans le config-repo existant. Les routes de la gateway sont dans `config-repo/api-gateway.yml`.

## 2. Démarrage

### Prérequis

Java 21. Maven n'est pas nécessaire : le wrapper `mvnw` / `mvnw.cmd` est fourni.

```bash
./mvnw clean package          # compile, lance les 30 tests et produit les 7 jars (-DskipTests pour aller plus vite)
```

### Option A — scripts (ordre de démarrage géré automatiquement)

```bash
./start-all.sh      # Linux / macOS / Git Bash — logs dans ./logs, arrêt : ./stop-all.sh
```
```powershell
.\start-all.ps1     # Windows PowerShell — une fenêtre par service
```

### Option B — à la main (IDE ou terminaux), dans cet ordre

1. `eureka-server` → http://localhost:8761
2. `config-server` : à lancer depuis la racine du projet ou depuis son module, il lit `./config-repo` ou `../config-repo`. Pour un autre emplacement, définir `CONFIG_REPO_PATH=file:/chemin/`.
3. `class-service`, `payment-service`, `notification-service`, `booking-service`
4. `api-gateway` → http://localhost:8080

Chaque service importe sa configuration avec `spring.config.import=optional:configserver:http://localhost:8888`. Au démarrage, le log doit afficher `Located environment: name=<service>`.

### Option C — Docker Compose (bonus)

```bash
./mvnw clean package -DskipTests
docker compose up --build        # 7 conteneurs, démarrage ordonné par healthchecks (~2 min)
```

J'ai testé ce lancement : les 7 conteneurs passent `healthy` et la collection Postman passe entièrement contre eux (`docs/newman-run-docker.txt`).

La console H2 de chaque service est disponible sur `http://localhost:<port>/h2-console`, avec l'URL JDBC `jdbc:h2:mem:<nom>db` et l'utilisateur `sa`.

## 3. Configuration centralisée (`config-repo/`)

| Fichier | Contenu |
|---|---|
| `application.yml` | Commun à tous : URL Eureka, actuator (`health`, `circuitbreakers`), JPA, console H2 |
| `class-service.yml` | Port 8091, `classdb`, nombre de tentatives du verrouillage optimiste (10) |
| `booking-service.yml` | Port 8092, `bookingdb`, délais métier (`payment-timeout: PT1H`, `cancellation-notice: PT24H`, `reminder-before: PT24H`), fréquence du scheduler (`PT5M`), Feign (timeouts, circuit breaker activé), Resilience4j |
| `payment-service.yml` | Port 8093, `paymentdb`, seuil de refus (`max-accepted-amount: 100.00`) |
| `notification-service.yml` | Port 8094, `notificationdb`, mot-clé qui simule une panne d'envoi |
| `api-gateway.yml` | Les 4 routes `/api/classes/**`, `/api/bookings/**`, `/api/payments/**`, `/api/notifications/**` vers `lb://<service>` |

## 4. API REST (toutes accessibles via la gateway : `http://localhost:8080`)

### class-service

| Méthode | URL | Réponses |
|---|---|---|
| GET | `/api/classes?category=&level=&status=&dateFrom=&dateTo=&location=&instructor=&page=0&size=10&sort=dateTime,asc` | 200 (page : `content` + `page.totalElements`…). Dates au format `yyyy-MM-dd`, bornes incluses |
| GET | `/api/classes/search?date=&dateFrom=&dateTo=&category=&level=&location=&instructor=&availableOnly=true` | 200. `date=yyyy-MM-dd` cible un jour précis. Par défaut, seulement les cours programmés, à venir et non complets |
| GET | `/api/classes/{id}` | 200 / 404 |
| POST | `/api/classes` | 201 + `Location` / 400 avec le détail par champ |
| PUT | `/api/classes/{id}` | 200 / 400 / 404 / 409 si `maxParticipants` < nombre d'inscrits |
| DELETE | `/api/classes/{id}` | 204 si le cours est supprimé (aucun inscrit), 200 + statut `CANCELLED` s'il a des inscrits |
| PATCH | `/api/classes/{id}/increment?spots=n` | 200 / 409 (plus de places, cours annulé ou passé) |
| PATCH | `/api/classes/{id}/decrement?spots=n` | 200 / 409 |

Validations appliquées, sur le DTO et aussi sur l'entité JPA : nom d'au moins 3 caractères, champs obligatoires, durée parmi 30, 45, 60 ou 90 (contrainte maison `@AllowedValues`), 5 ≤ `maxParticipants` ≤ 30, `currentParticipants` ≤ `maxParticipants`, prix ≥ 5,00, date dans le futur. Les filtres texte (`location`, `instructor`) sont insensibles à la casse et acceptent une valeur partielle. Ils sont construits avec des `Specification` JPA.

### booking-service

| Méthode | URL | Réponses |
|---|---|---|
| GET | `/api/bookings[?status=]`, `/api/bookings/{id}`, `/api/bookings/user/{userId}` | 200 / 404 |
| POST | `/api/bookings` | **201** `PENDING_PAYMENT` / 400 / 404 (cours inconnu) / **409** (plus de places) / 503 (service en panne) |
| PATCH | `/api/bookings/{id}/confirm` | **200** `CONFIRMED`, ou **200** `CANCELLED` avec `paymentStatus: FAILED` si le paiement est refusé / 400 (carte sans `cardLastFour`) / **409** (paiement expiré ou mauvais statut) |
| PATCH | `/api/bookings/{id}/cancel` | **200** `CANCELLED` (+ remboursement) / **409** (moins de 24 h avant le cours, ou déjà annulée ou terminée) |
| PATCH | `/api/bookings/{id}/complete` | 200 `COMPLETED` / 409 |
| GET | `/api/bookings/expired` | Réservations `PENDING_PAYMENT` dont le délai de paiement est dépassé |
| POST | `/api/bookings/expired/cancel` | Déclenche tout de suite le traitement du scheduler |
| PATCH | `/api/bookings/{id}/simulate-expiration` | *Démo uniquement* (`demo-endpoints-enabled`) : place `paymentDeadline` dans le passé pour rejouer le scénario « paiement expiré » sans attendre une heure |

### payment-service

| Méthode | URL | Réponses |
|---|---|---|
| POST | `/api/payments` | 201, avec `status` `SUCCESS` si le montant est < 100 €, `FAILED` sinon / 409 si la réservation est déjà payée / 400 (carte sans `cardLastFour`…) |
| GET | `/api/payments/booking/{bookingId}` | Dernier paiement de la réservation / 404 |
| POST | `/api/payments/{id}/refund` | 200 `REFUNDED` / 409 si le paiement n'est pas `SUCCESS`. La règle « au moins 24 h avant le cours » est contrôlée par booking-service, qui seul connaît la date du cours, avant d'appeler cet endpoint |
| GET | `/api/payments/user/{userId}`, `/api/payments/{id}` | 200 |

Les références `PAY-XXXXX` et `BK-XXXXX` sont générées aléatoirement et leur unicité est vérifiée. Un `transactionId` est généré s'il n'est pas fourni.

### notification-service

| Méthode | URL | Réponses |
|---|---|---|
| POST | `/api/notifications` | 201, avec `status` `SENT` (envoi tracé dans le log `[EMAIL] à=…`), ou `FAILED` si le destinataire contient « fail » (panne simulée) |
| GET | `/api/notifications/user/{userId}` | Historique |
| GET | `/api/notifications/pending[?includeFailed=true]` | Notifications `PENDING` et `FAILED` à relancer |
| PATCH | `/api/notifications/{id}/retry` | 200 / 409 si déjà envoyée |

Toutes les erreurs partagent le même format : `{timestamp, status, error, message, path, details?}`.

## 5. La saga « Reserve Now, Pay Later »

`BookingService` est l'**orchestrateur**. Les méthodes de la saga ne sont volontairement pas `@Transactional` : on ne garde pas une transaction base de données ouverte pendant des appels HTTP. Chaque étape déjà réalisée a sa **compensation**.

| Cas | Étapes | Compensation en cas d'échec |
|---|---|---|
| **1. Réservation** `POST /api/bookings` | ① `GET /api/classes/{id}` : le cours existe, est `SCHEDULED`, à venir, avec assez de places ; snapshot du nom, de l'instructeur, de la date et du prix → ② `PATCH /increment?spots=n` → ③ enregistrement `PENDING_PAYMENT` : `paymentDeadline` = maintenant + 1 h (plafonnée au début du cours), `cancellationDeadline` = date du cours − 24 h, référence `BK-XXXXX`, `totalAmount` = prix × places → ④ notification `BOOKING_CONFIRMATION` (« Payez avant … ») → ⑤ **201** | Si ② échoue (409) : rien à compenser, le client reçoit 409. Si ③ échoue : **`PATCH /decrement`** pour rendre les places |
| **2. Plus de places** | Places disponibles à l'étape ①, mais une autre réservation passe entre-temps : class-service renvoie **409** à l'étape ② | Aucune réservation créée → **409** « Plus de places disponibles » |
| **3. Paiement** `PATCH /{id}/confirm` | ① statut `PENDING_PAYMENT` et délai de paiement non dépassé → ② `POST /api/payments` → ③ `SUCCESS` : `CONFIRMED`, `paymentId`, `paymentReference` et `paymentStatus` enregistrés → ④ notification `PAYMENT_CONFIRMATION` → **200** | Délai dépassé : la réservation est annulée et les places libérées, puis **409**. Paiement `FAILED` (≥ 100 €) : **compensation**, la réservation passe en `CANCELLED` (`PAYMENT_FAILED`), `PATCH /decrement`, notification `BOOKING_CANCELLED`, puis **200** avec la réservation mise à jour. Si la réservation a été annulée ou a expiré *pendant* l'appel au paiement, l'enregistrement échoue sur le `@Version` : le paiement est **remboursé**, puis 409 |
| **4. Annulation** `PATCH /{id}/cancel` | ① le statut n'est ni `CANCELLED` ni `COMPLETED`, et `cancellationDeadline` n'est pas dépassée (sinon **409**) → ② si `CONFIRMED` : `POST /api/payments/{id}/refund` → `PATCH /decrement` → ③ `CANCELLED` → ④ notification `BOOKING_CANCELLED` → **200** | Si le remboursement échoue, rien n'est modifié (503 ou 409) |

Précisions sur mes choix :

- **Paiement refusé.** L'énoncé laisse le choix : « CANCELLED (ou reste PENDING_PAYMENT pour retenter) ». Le refus simulé est déterministe (≥ 100 €), une nouvelle tentative échouerait forcément. J'ai donc choisi `CANCELLED` avec libération immédiate des places : c'est une vraie compensation de saga. La réponse reste **200 OK** avec les détails mis à jour, comme demandé, et le client lit `status: CANCELLED`, `paymentStatus: FAILED` et `cancellationReason: PAYMENT_FAILED`.
- **Places libérées aussi pour une réservation `PENDING_PAYMENT` annulée**, et pas seulement pour une `CONFIRMED` : les places sont prises dès la réservation (étape ②), elles doivent donc être rendues dans tous les cas.
- **Notifications en best effort.** Si notification-service est en panne, la réservation n'est pas bloquée : le fallback journalise l'échec et la saga continue.
- Si class-service est injoignable au moment de rendre des places, la réservation est tout de même annulée. L'écart est journalisé avec la mention `[A RÉCONCILIER]`.
- **Annulation : on enregistre d'abord, on libère ensuite.** `Booking` a aussi un `@Version`. Le statut `CANCELLED` est enregistré *avant* d'appeler `PATCH /decrement`. Si deux annulations arrivent en même temps (utilisateur et scheduler par exemple), une seule passe le contrôle de version : les places ne sont libérées qu'une fois et l'autre requête reçoit 409.
- La `paymentDeadline` vaut `bookingDate + 1 h`, sauf si le cours commence avant : elle est alors plafonnée à l'heure du cours, car on ne peut pas payer un cours déjà commencé.

## 6. Verrouillage optimiste (class-service)

```java
@Version private Long version;          // Hibernate : UPDATE ... SET ..., version = version + 1 WHERE id = ? AND version = ?

public void incrementParticipants(int spots) {
    if (currentParticipants + spots > maxParticipants) throw new NoSpotsAvailableException("Plus de places disponibles ...");
    currentParticipants += spots;
}
```

Deux réservations simultanées lisent la même version du cours. La première met à jour la ligne. L'`UPDATE` de la seconde ne touche aucune ligne : Hibernate lève une `OptimisticLockException`, ce qui empêche de perdre une mise à jour.

`FitnessClassService.updateWithOptimisticRetry` relance alors l'opération dans une **nouvelle transaction** : il relit le cours et **refait la vérification des places** sur la dernière version. Il fait jusqu'à 10 tentatives (`optimistic-lock-retries` dans `config-repo`). S'il n'y a plus de place, la réponse est **409** « Plus de places disponibles ». Si le conflit persiste après toutes les tentatives, ce qui n'arrive que sous très forte concurrence, la réponse est **409** « modifié en parallèle, réessayez ». Dans tous les cas, **aucune surréservation n'est possible**.

Deux tests le prouvent (`OptimisticLockingIntegrationTest`) :

- `secondUpdateWithStaleVersion_isRejected` : une copie périmée de l'entité est refusée et la valeur en base reste correcte.
- `concurrentBookings_neverExceedCapacity` : 12 threads réservent 5 places en même temps sur un cours de 30. Avec 50 tentatives autorisées dans ce test, **exactement 6 réussissent**, `currentParticipants` = 30, et les 6 autres reçoivent 409.

## 7. Feign + Circuit Breaker (booking-service)

- `@FeignClient(name = "class-service" | "payment-service" | "notification-service", fallbackFactory = …)`, avec résolution par Eureka et répartition de charge par Spring Cloud LoadBalancer.
- Le client HTTP est Apache HC5 (`feign-hc5`), car le client par défaut de Feign ne sait pas envoyer de requêtes `PATCH`.
- `spring.cloud.openfeign.circuitbreaker.enabled=true` : chaque méthode a son circuit Resilience4j. Réglages : fenêtre de 10 appels, seuil de 50 %, 10 s en état ouvert, timeout de 6 s.
- Les **erreurs métier 4xx ne comptent pas comme des pannes** (`ignore-exceptions: FeignClientException`). Le `FallbackFactory` les retransmet : un 409 de class-service devient `NoSpotsAvailableException` (409), un 404 devient 404. Les vraies pannes (service arrêté, timeout, circuit ouvert) donnent un **503** explicite.
- Test manuel : avec class-service arrêté, 7 `POST /api/bookings` renvoient 503, puis le message devient *« class-service indisponible (circuit breaker ouvert) »*. Sur `/actuator/circuitbreakers`, on voit `ClassClientgetFitnessClassLong OPEN 50.0%`.

## 8. Scheduler (booking-service, `@EnableScheduling`)

| Tâche | Fréquence | Traitement |
|---|---|---|
| `cancelExpiredBookings` | toutes les 5 min (`fitconnect.scheduler.expiration-rate`) | Réservations `PENDING_PAYMENT` avec `paymentDeadline < now()` → `CANCELLED` (`PAYMENT_TIMEOUT`), `PATCH /decrement`, notification `BOOKING_CANCELLED` |
| `sendReminders` | toutes les 5 min | Réservations `CONFIRMED` dont le cours commence dans les prochaines 24 h et qui n'ont pas encore reçu de rappel → `BOOKING_REMINDER`, puis `reminderSent = true` pour ne pas l'envoyer deux fois |

Pour le rappel, je cherche les cours dont la date tombe entre maintenant et maintenant + 24 h, avec un indicateur `reminderSent`. Une égalité stricte avec « maintenant + 24 h » ne fonctionnerait pas avec un job qui passe toutes les 5 minutes.

## 9. Tests (30 tests, tous verts)

```bash
./mvnw test
```

| Module | Classe | Tests |
|---|---|---|
| class-service | `FitnessClassTest` (unitaire) | `shouldThrowException_whenNoSpotsAvailable` (10 places, 9 inscrits, +2 → `NoSpotsAvailableException`), incrémentation, dernière place, cours annulé, décrémentation |
| class-service | `OptimisticLockingIntegrationTest` | version périmée refusée ; 12 réservations concurrentes → exactement 6 réussissent |
| class-service | `FitnessClassControllerIntegrationTest` | création et lecture, validations (400), filtres et pagination et recherche, 409 sur increment, suppression ou annulation |
| booking-service | `BookingServiceTest` (unitaire, Mockito et doubles) | **`shouldCreateBooking_whenSpotsAvailable`** (10 places, 5 inscrits, +2 → `PENDING_PAYMENT`, 7 inscrits), **`shouldThrowException_whenNoSpotsAvailable`**, conflit entre la vérification et l'incrémentation, **`shouldCancelBookingAndRefund_whenWithinDeadline`**, annulation hors délai, paiement refusé avec compensation, paiement expiré, remboursement si la réservation change pendant le paiement, places non libérées deux fois lors d'annulations simultanées |
| booking-service | `BookingFlowIntegrationTest` (`@SpringBootTest` + MockMvc + H2) | **`shouldCompleteFullBookingFlow`** (création du cours → réservation → paiement → `CONFIRMED` → places diminuées → notifications), **`shouldCancelExpiredBookings`** (délai passé → scheduler → `CANCELLED` → places rendues), rappel envoyé une seule fois |
| payment-service | `PaymentControllerIntegrationTest` | < 100 € `SUCCESS` puis `REFUNDED` (et un second remboursement donne 409), ≥ 100 € `FAILED`, double paiement 409, carte sans 4 derniers chiffres 400 |
| notification-service | `NotificationControllerIntegrationTest` | `SENT`, panne simulée `FAILED` puis `pending` et `retry`, validations |

Dans les tests d'intégration du booking-service, les trois services distants sont remplacés par des doubles en mémoire qui appliquent les mêmes règles (`support/Fake*Client`). Le flux avec les **vrais** services, Eureka et la gateway est vérifié par la collection Postman ci-dessous.

## 10. Collection Postman

Importer `postman/FitConnect.postman_collection.json` (variable `baseUrl` = `http://localhost:8080`), puis lancer la collection complète dans le Runner. Les dossiers s'enchaînent et se transmettent les identifiants par variables.

| Dossier | Requêtes |
|---|---|
| 1. Gestion des cours | Créer un cours · Lister (pagination) · Filtrer YOGA/INTERMEDIATE · Détails · Recherche · Cours invalide (400) |
| 2. Réservation | Réserver 2 places (201) · Vérifier `PENDING_PAYMENT` · Vérifier 2 places prises |
| 3. Paiement | Payer par carte (200) · Vérifier `CONFIRMED` · Paiement `SUCCESS` · Notifications |
| 4. Annulation | Annuler (200) · Remboursement `REFUNDED` · Places libérées · Notification `BOOKING_CANCELLED` |
| 5. Scénarios d'erreur | **Surréservation (409)** · **Paiement expiré (409)** · **Annulation hors délais (409)** · Paiement refusé ≥ 100 € (réservation `CANCELLED`, paiement `FAILED`, places libérées) · Carte sans `cardLastFour` (400) · Cours inexistant (404) |
| 6. Scheduler et consultation | Réservations expirées · Traitement d'expiration à la demande · Historiques |

Exécution réelle sur la stack complète (7 applications, via la gateway) avec Newman : **41 requêtes, 69 assertions, 0 échec**. La sortie est dans `docs/newman-run.txt` pour le lancement avec les jars et dans `docs/newman-run-docker.txt` pour Docker Compose. `docs/scheduler-log.txt` montre le scheduler annuler tout seul une réservation expirée.

```bash
npx newman run postman/FitConnect.postman_collection.json
```

## 11. Structure du projet

```
fitconnect/
├── pom.xml                      parent Maven (Boot 3.5.16, Cloud 2025.0.3), 7 modules
├── config-repo/                 configuration servie par config-server
├── eureka-server/  config-server/  api-gateway/
├── class-service/               model (FitnessClass @Version) · dto · repository (+ Specifications) · service · web · exception · validation
├── booking-service/             client (Feign + fallbacks) · service (saga, notifier) · scheduler · web · model · repository
├── payment-service/             model · dto · repository · service (simulation) · web
├── notification-service/        model · dto · repository · service (envoi simulé) · web
├── postman/                     collection Postman
├── docs/                        résultats Newman et des tests
├── docker-compose.yml           bonus
└── start-all.sh / stop-all.sh / start-all.ps1
```

## 12. Limites et pistes d'amélioration

- Bases H2 en mémoire : les données sont perdues à chaque redémarrage, ce qui est voulu pour un TP. En production, il faudrait une base PostgreSQL par service.
- La saga est orchestrée en appels synchrones. Pour plus de robustesse, on pourrait passer à des événements (Kafka ou RabbitMQ) avec le pattern outbox, et réconcilier automatiquement les écarts marqués `[A RÉCONCILIER]`.
- Il n'y a pas d'authentification : `userId`, `userEmail` et `userName` sont fournis par le client, comme dans l'énoncé. Un user-service ou un JWT à la gateway serait l'étape suivante.
- L'annulation d'un cours (`DELETE` avec des inscrits) passe le cours en `CANCELLED` mais ne prévient pas encore les inscrits. Il faudrait que booking-service envoie `CLASS_CANCELLED` à chacun.

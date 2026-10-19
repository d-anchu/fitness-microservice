# AI Service (`aiservice`) — Full Context Document

> **Purpose of this document:** a complete, self-contained description of the `aiservice`
> module of the `fitness-microservice` project — its responsibility, architecture, every
> source file, configuration, runtime contracts, observed behaviour and known issues.
> It is written so that a reviewer (human or LLM) can propose improvements **without
> needing access to the repository**.

---

## 0. How to use this document

| Section | What you get |
|---|---|
| §1–§3 | What the service is and why it exists |
| §4–§5 | Exact stack, dependencies, configuration |
| §6 | **Full source of every file** in the module |
| §7–§9 | Runtime contracts: RabbitMQ message, LLM prompt, REST API |
| §10 | Measured evidence from a live local stack |
| §11 | Prioritised improvement backlog |
| §12 | Build / run / test commands |

Everything in §6 is verbatim current source. §10 is observed runtime data, not speculation.

---

## 1. Executive summary

`aiservice` is a **Spring Boot microservice that consumes fitness activities from RabbitMQ,
asks a Large Language Model (Groq / `openai/gpt-oss-120b`) for coaching analysis, and exposes
that analysis over REST.**

It is the "intelligence" leg of a 4-service stack:

```
userservice ──┐
              ├──> activityservice ──(RabbitMQ)──> aiservice ──> Groq LLM API
eureka        │                                      │
              └──────────────────────────────────────┴──> MongoDB
```

**Current maturity: early / work-in-progress.** The LLM call works end to end, but the
service is **incomplete in a critical way — the generated recommendation is never persisted
or returned**, so the REST API it exposes always responds with empty/404 results. Details in §10.

---

## 2. System context

### 2.1 Services in the monorepo

The root `pom.xml` is a **Maven aggregator** (`<packaging>pom</packaging>`) with four modules.
There is **no shared parent POM** — each module independently inherits
`spring-boot-starter-parent`.

| Module | `spring.application.name` | Port | Storage | Role |
|---|---|---|---|---|
| `eureka` | (service registry) | 8761 | — | Netflix Eureka service discovery |
| `userservice` | `user-service` | 8081 | PostgreSQL `localhost:5433/fitness_user_id` | Users + registration + `/validate` endpoint |
| `activityservice` | `activity-service` | 8082 | MongoDB `fitnessactivity` | Activity CRUD + RabbitMQ publisher |
| `aiservice` | `ai-service` | 8083 | MongoDB `fitnessrecommendation` | LLM recommendations + REST read API |

### 2.2 External infrastructure

| Component | Address | Used by |
|---|---|---|
| MongoDB | `localhost:27017` | `activityservice`, `aiservice` |
| PostgreSQL | `localhost:5433` | `userservice` |
| RabbitMQ | `localhost:5672`, guest/guest | `activityservice` (publish), `aiservice` (consume) |
| Eureka | `http://localhost:8761/eureka/` | all three business services register here |
| Groq API | `https://api.groq.com/openai/v1/chat/completions` | `aiservice` only |

### 2.3 Message broker topology

Both `activityservice` and `aiservice` declare the **same** exchange/queue/binding
(duplicated config, see §11-I10):

| Setting | Value |
|---|---|
| Exchange | `fitness.exchange` (DirectExchange) |
| Queue | `activity.queue` (durable, `new Queue(queue, true)`) |
| Routing key | `activity.tracking` |
| Message converter | `JacksonJsonMessageConverter` (JSON body) |

---

## 3. Responsibility and runtime flow

```
1. Client  POST http://localhost:8082/api/activities   (X-User-Id header, JSON body)
2. activityservice
      a. calls userservice  GET /api/users/{userId}/validate   (WebClient, blocking)
      b. saves Activity document into Mongo DB `fitnessactivity.activities`
      c. publishes the SAVED Activity as a JSON message to
         fitness.exchange / activity.tracking
      d. returns ActivityResponse to the client (200 OK)
            ^ NOTE: this response contains NO recommendation. It is fire-and-forget.
3. aiservice  (asynchronous, decoupled)
      a. @RabbitListener(queues = "activity.queue") receives the Activity JSON
      b. ActivityMessageListener.processActivity(activity)          [LOGS ONLY]
      c. -> ActivityAIService.generateRecommendation(activity)
             - createPromptForActivity(activity)  builds a text prompt
             - GroqService.getAnswer(prompt)      POSTs to Groq, blocks on the response
      d. the resulting JSON string is written to the application log
4. Client  GET http://localhost:8083/api/recommendations/user/{userId}
      -> RecommendationService -> RecommendationRepository (Mongo)
      -> ALWAYS EMPTY, because step 3d never writes to Mongo
```

**The critical gap:** there is no link between step 3 and step 4. The AI output is only
logged, never mapped into a `Recommendation` entity and never saved. Confirmed empirically
in §10.

### 3.1 Threading / blocking model

- The app is a **servlet (Spring MVC) application**, not WebFlux — despite depending on
  `spring-boot-starter-webflux` (used only for `WebClient`).
- `GroqService` uses `.block()` on a reactive `WebClient` call. This blocks the **RabbitMQ
  listener container thread** (`ntContainer#0-1` in logs) for the entire LLM round trip
  (~2 s observed, `completion_time: 2.10s`).
- There is **no timeout** configured on that call (see §11-I3).

---

## 4. Tech stack and build

### 4.1 Versions

| Item | Value |
|---|---|
| Java | 21 |
| Spring Boot | `4.1.1` (via `spring-boot-starter-parent`) |
| Spring Cloud | `2025.1.3` (`spring-cloud-dependencies` BOM) |
| Build | Maven, wrapper included (`mvnw`, `mvnw.cmd`) |
| Packaging | Executable JAR (`spring-boot-maven-plugin`) |

### 4.2 `aiservice/pom.xml` dependencies (verbatim list)

```
org.springframework.boot:spring-boot-starter-data-mongodb
org.springframework.boot:spring-boot-starter-webmvc
org.springframework.boot:spring-boot-starter-amqp
org.projectlombok:lombok                        (optional)
org.springframework.boot:spring-boot-starter-webflux        <-- for WebClient only
org.springframework.cloud:spring-cloud-starter-netflix-eureka-client

test scope:
org.springframework.boot:spring-boot-starter-data-mongodb-test
org.springframework.boot:spring-boot-starter-webmvc-test
```

Notes for a reviewer:
- Spring Boot 4 renamed the web starter to **`-webmvc`** (not `-web`).
- Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) is available transitively and its
  bean is auto-configured by Spring Boot.
- **No** `spring-boot-starter-validation`, **no** `spring-boot-starter-actuator`,
  **no** `spring-boot-starter-test` (the two `-test` starters above are Boot 4 replacements).
- Lombok is wired into `maven-compiler-plugin` `annotationProcessorPaths` for both
  `default-compile` and `default-testCompile`.

---

## 5. Configuration

### 5.1 `aiservice/src/main/resources/application.yml` (FULL, verbatim)

```yaml
server:
  port: 8083

spring:
  application:
    name: ai-service
  config:
    # Load secrets (GROQ_API_KEY) from a local .env file so the service works
    # from a terminal as well as from the IDE. Spring reads the KEY=value lines
    # as a .properties file. Both paths are optional, which means the app still
    # starts (failing later with a clear placeholder error) if no .env exists.
    #   - .env     -> used when the working directory is the project root
    #   - ../.env  -> used when the working directory is the aiservice folder
    import:
      - optional:file:.env[.properties]
      - optional:file:../.env[.properties]
  data:
    mongodb:
      uri: mongodb://localhost:27017/fitnessrecommendation
      database: fitnessrecommendation
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest


eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/


rabbitmq:
  exchange:
    name: fitness.exchange
  queue:
    name: activity.queue
  routing:
    key: activity.tracking

# Groq uses OpenAI-compatible chat completions:
#   POST {groq.api.url} with header "Authorization: Bearer <key>"
# Override via env vars when deploying: GROQ_API_URL, GROQ_API_KEY and GROQ_MODEL
groq:
  api:
    url: ${GROQ_API_URL:https://api.groq.com/openai/v1/chat/completions}
    key: ${GROQ_API_KEY}
    # llama-3.3-70b-versatile is not enabled on every Groq account; gpt-oss-120b is available.
    model: ${GROQ_MODEL:openai/gpt-oss-120b}
```

### 5.2 Secrets handling

- `GROQ_API_KEY` has **no default** → if `.env` is absent the context fails to start with
  an unresolved-placeholder error. This is intentional and commented in the file.
- `.env` is loaded as a properties file from either the repo root or the module's parent dir,
  so it works from VS Code, a terminal, or the IDE.
- `.env` is git-ignored; `.env.example` is committed:

```properties
GROQ_API_KEY=your-groq-api-key-here
# GROQ_API_URL=https://api.groq.com/openai/v1/chat/completions
# GROQ_MODEL=openai/gpt-oss-120b
```

### 5.3 Configuration properties consumed by the code

| Property | Injected into | Field |
|---|---|---|
| `groq.api.url` | `GroqService` | `groqApiUrl` (`@Value`) |
| `groq.api.key` | `GroqService` | `groqApiKey` (`@Value`) |
| `groq.api.model` | `GroqService` | `groqModel` (`@Value`) |
| `rabbitmq.queue.name` | `RabbitMqConfig` | `queue` (`@Value`) |
| `rabbitmq.exchange.name` | `RabbitMqConfig` | `exchange` (`@Value`) |
| `rabbitmq.routing.key` | `RabbitMqConfig` | `routingKey` (`@Value`) |

> Note: `rabbitmq.queue.name` / `.exchange.name` / `.routing.key` are **custom** keys, not
> Spring Boot's native `spring.rabbitmq.*` properties. `@RabbitListener(queues = "activity.queue")`
> is **hard-coded** rather than referencing the property — a duplication risk (§11-I10).

---

## 6. Full source inventory

### 6.0 File tree

```
aiservice/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
└── src/
    ├── main/
    │   ├── java/com/fitness/aiservice/
    │   │   ├── AiserviceApplication.java
    │   │   ├── config/
    │   │   │   ├── MongoConfig.java
    │   │   │   ├── RabbitMqConfig.java
    │   │   │   └── WebClientConfig.java
    │   │   ├── controller/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── RecommendationController.java
    │   │   ├── model/
    │   │   │   ├── Activity.java
    │   │   │   └── Recommendation.java
    │   │   ├── repository/
    │   │   │   └── RecommendationRepository.java
    │   │   └── service/
    │   │       ├── ActivityAIService.java
    │   │       ├── ActivityMessageListener.java
    │   │       ├── GroqApiException.java
    │   │       ├── GroqService.java
    │   │       └── RecommendationService.java
    │   └── resources/application.yml
    └── test/java/com/fitness/aiservice/
        ├── AiserviceApplicationTests.java
        └── service/GroqServiceTest.java
```

Total: **15 Java files** (13 main + 2 test) and 1 YAML file.

---

### 6.1 `AiserviceApplication.java`

Standard Boot entry point. No `@EnableDiscoveryClient` (not needed — the Eureka starter
auto-registers when `eureka.client.*` is present). No `@EnableRabbit` (also not needed;
`@RabbitListener` infra is auto-configured).

````java
package com.fitness.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiserviceApplication.class, args);
	}

}
````

---

### 6.2 `config/MongoConfig.java`

Enables Mongo auditing so `@CreatedDate` on `Recommendation.createdAt` is populated.
Empty otherwise.

````java
package com.fitness.aiservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
````

---

### 6.3 `config/RabbitMqConfig.java`

Declares the consumer-side broker topology plus a JSON `MessageConverter` and a
`RabbitTemplate`. Note the `RabbitTemplate` bean is **never used** by any AI-service
code — the module only consumes (dead configuration, §11-I9).

````java
package com.fitness.aiservice.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${rabbitmq.queue.name}")
    private String queue;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;


    @Bean
    public Queue activityQueue(){

        return new Queue(queue,true);
    }

    @Bean
    public DirectExchange activityExchange()
    {
        return new DirectExchange(exchange);

    }

    @Bean
    public Binding activityBinding(Queue activityQueue,DirectExchange activityExchange)
    {
        return  BindingBuilder.bind(activityQueue).to(activityExchange).with(routingKey);
    }


    @Bean
    public MessageConverter jsonMessageConverter()
    {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter)
    {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }


}
````

---

### 6.4 `config/WebClientConfig.java`

Explicitly declares `WebClient.Builder` because reactive auto-configuration does not apply
in a servlet application.

````java
package com.fitness.aiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * In a servlet (MVC) application the reactive WebClient.Builder bean is NOT
 * auto-configured — it only is for WebFlux apps — so we declare it explicitly.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
````

---

### 6.5 `model/Activity.java`

**Inbound DTO** deserialized from the RabbitMQ message. Deliberately **not** a `@Document`
— the AI service does not own activity data.

Important: `@Data` alone provides a no-args constructor (no `final`/`@NonNull` fields), which
is what Jackson needs. Fields `distance` and `heartRate` exist here but are **not present in
the published payload** (see §7 and §11-I1).

````java
package com.fitness.aiservice.model;


import lombok.Data;

import org.springframework.data.annotation.Id;


import java.time.LocalDateTime;
import java.util.Map;


@Data


public class Activity {
    @Id
    private String id;
    private String userId;
    private String type;
    private Integer duration;
    private Integer caloriesBurned;
    private Double distance;
    private Integer heartRate;
    private LocalDateTime startTime;


    private Map<String,Object> additionMetrics;


    private LocalDateTime createdAt;


    private LocalDateTime updatedAt;

}
````

> `type` is a `String` here but an `ActivityType` **enum** on the publisher side
> (`activityservice`). Jackson serializes the enum to its name (`"RUNNING"`), so `String`
> works — but it silently accepts any value and loses type safety (§11-I7).

---

### 6.6 `model/Recommendation.java`

**Persistent entity** mapped to Mongo collection `recommendations`.

**Field mismatch warning:** this entity has `recommendation` (String), `suggestions`
(List&lt;String&gt;) and `safety` (List&lt;String&gt;) — but the LLM prompt asks for
`analysis`, `recommendations`, `improvements` and `suggestions` (see §8). The two schemas do
not line up, which is a root cause of why nothing is persisted (§11-I2).

````java
package com.fitness.aiservice.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;


@Document(collection = "recommendations")
@Data
@Builder
public class Recommendation {
    @Id
    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private String recommendation;
    private List<String> suggestions;
    private List<String> safety;


    @CreatedDate
    private LocalDateTime createdAt;
}
````

> Missing `@NoArgsConstructor` / `@AllArgsConstructor`. `@Builder` alone suppresses the
> implicit no-args constructor only when combined with other Lombok annotations — here
> `@Data` + `@Builder` means Lombok generates an all-args constructor for the builder and
> **no** no-args constructor, which MongoDB's default persistence constructor can still use
> via its all-args constructor. Worth verifying/annotating explicitly (§11-I8).

---

### 6.7 `repository/RecommendationRepository.java`

Spring Data Mongo repository. Both derived queries are used by `RecommendationService`.

````java
package com.fitness.aiservice.repository;

import com.fitness.aiservice.model.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    List<Recommendation> findByUserId(String userId);

    Optional<Recommendation> findByActivityId(String activityId);
}
````

---

### 6.8 `service/ActivityAIService.java` — prompt builder

Reads an `Activity`, defensively fills missing numeric fields, and builds the LLM prompt.

Key behaviour:
- `distance` falls back to `additionMetrics["distance"]` — **this key is correct for live
  data** (the wire payload really does contain `additionMetrics.distance`, verified in §10.4),
  although the repo's sample `act.json` uses `distanceKm`, which yields `0.0` ✗ — see §7.3.
- `heartRate` falls back to `additionMetrics["heartRate"]` — **this key never exists** in
  the real payload (it uses `maxHeartRate`), so heart rate is **always sent as 0** — see §11-I1.
- The top-level `activity.getDistance()` / `activity.getHeartRate()` fields on the DTO are
  **never populated** (the producer has no such fields) — verified in §10.4 finding B.
- The JSON schema printed in the prompt is what the model is told to imitate.

````java
package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {

    private final GroqService groqService;

    public String generateRecommendation(Activity activity) {
        String prompt = createPromptForActivity(activity);
        String aiResponse = groqService.getAnswer(prompt);
        log.info("RESPONSE FROM AI: {} ", aiResponse);
        return aiResponse;
    }

    private String createPromptForActivity(Activity activity) {
        String type = activity.getType() != null ? activity.getType() : "UNKNOWN";
        int duration = activity.getDuration() != null ? activity.getDuration() : 0;
        int calories = activity.getCaloriesBurned() != null ? activity.getCaloriesBurned() : 0;

        // distance & heart rate aren't top-level fields on the wire yet —
        // fall back to the additionMetrics map before defaulting to 0.
        Map<String, Object> metrics = activity.getAdditionMetrics();
        Double distance = activity.getDistance();
        if (distance == null) {
            if (metrics != null && metrics.get("distance") instanceof Number n) {
                distance = n.doubleValue();
            } else {
                distance = 0.0;
            }
        }
        Integer heartRate = activity.getHeartRate();
        if (heartRate == null) {
            if (metrics != null && metrics.get("heartRate") instanceof Number n) {
                heartRate = n.intValue();
            } else {
                heartRate = 0;
            }
        }

        return String.format("""
            Analyze this fitness activity and provide detailed recommendations in the following JSON format:
            {
              "analysis": {
                "overall": "Overall analysis here",
                "pace": "Pace analysis here",
                "heartRate": "Heart rate analysis here",
                "caloriesBurned": "Calories analysis here"
              },
              "recommendations": [
                "Recommendation 1",
                "Recommendation 2"
              ],
              "improvements": [
                {
                  "area": "Area name",
                  "recommendation": "Detailed recommendation"
                }
              ],
              "suggestions": [
                {
                  "workout": "Workout name",
                  "description": "Workout description"
                }
              ]
            }

            Respond with ONLY the raw JSON object. Do not wrap it in markdown code
            fences (no ```json) and do not add any text before or after the JSON.

            Activity Details:
            - Type: %s
            - Duration: %d minutes
            - Calories Burned: %d
            - Distance: %.2f km
            - Heart Rate: %d bpm
            """,
                type,
                duration,
                calories,
                distance,
                heartRate
        );
    }
}


> **Unused import:** `com.fitness.aiservice.model.Recommendation` is imported but never
> referenced (§11-I9). The class name also advertises "AI + Recommendation mapping" that it
> does not do.

---

### 6.9 `service/ActivityMessageListener.java` — RabbitMQ consumer

**This is the only bridge between the broker and the AI, and it does nothing but log.**
The return value of `generateRecommendation` is passed straight into a log statement. There
is no persistence, no error handling, no acknowledgement control.

````java
package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService aiService;

    @RabbitListener(queues = "activity.queue")
    public void processActivity(Activity activity)
    {

        log.info("Received Activity for processing:{}",activity.getId());
        log.info("Generated Recommendation for Activity:{}",aiService.generateRecommendation(activity));

    }


}
````

> `import lombok.Value;` is **unused** (§11-I9).
> Queue name is hard-coded instead of using `rabbitmq.queue.name` (§11-I10).

---

### 6.10 `service/GroqService.java` — the LLM client

The heart of the module. Uses a blocking `WebClient` call to Groq's OpenAI-compatible
chat-completions endpoint.

What it does now (recently refactored):
1. Sends `"response_format": {"type":"json_object"}` so Groq returns a **valid JSON object**.
2. Parses the HTTP body with Jackson and returns **only** `choices[0].message.content`.
3. Strips accidental markdown fences (defensive).
4. Raises `GroqApiException` on non-2xx and on unparsable/empty content.

Before this change, `getAnswer` returned the **entire Groq envelope** (`id`, `object`,
`usage`, `x_groq`, and the model's `reasoning` field) — which is why the application log
looked like a giant blob instead of a JSON recommendation.

````java
package com.fitness.aiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.model}")
    private String groqModel;

    public GroqService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the Groq chat-completions endpoint and returns ONLY the assistant
     * message content (choices[0].message.content) as a JSON string.
     * <p>
     * The HTTP body itself is an OpenAI-compatible envelope (id/object/usage/
     * choices/reasoning...), so returning it verbatim would hand the caller the
     * envelope instead of the model's answer.
     */
    public String getAnswer(String question) {
        // Groq is OpenAI-compatible: { "model": ..., "messages": [{ "role": ..., "content": ... }] }
        // response_format=json_object makes Groq guarantee the content is a valid JSON object.
        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of("role", "user", "content", question)
                ),
                "response_format", Map.of("type", "json_object")
        );

        String rawResponse = webClient.post()
                .uri(groqApiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .bodyValue(requestBody)
                .retrieve()
                // Surface Groq's error body (invalid key, unknown model, rate limit) instead of a bare exception.
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.error("Groq API call failed - status={} body={}", response.statusCode().value(), body);
                            return Mono.error(new GroqApiException(response.statusCode().value(), body));
                        }))
                .bodyToMono(String.class)
                .block();

        log.debug("RAW RESPONSE FROM GROQ: {}", rawResponse);
        return extractMessageContent(rawResponse);
    }

    /**
     * Pulls choices[0].message.content out of the Groq envelope. The "reasoning"
     * field that reasoning models (gpt-oss) emit lives next to "content" and is
     * intentionally ignored.
     * <p>
     * Package-private so it can be unit tested without hitting the live API.
     */
    String extractMessageContent(String rawResponse) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new GroqApiException(200, "Groq response was not valid JSON: " + rawResponse);
        }

        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new GroqApiException(200, "Groq response contained no choices[0].message.content: " + rawResponse);
        }
        return stripMarkdownFences(content.asText());
    }

    /**
     * Defensive only: even in json_object mode some models still wrap the object
     * in a ```json ... ``` fence.
     */
    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutOpeningFence = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutOpeningFence.replaceFirst("\\s*```$", "").trim();
    }
}
````

**Remaining concerns with this class**
- `.block()` with **no timeout** → a hung Groq connection blocks the listener thread forever
  (§11-I3).
- **No retry / backoff** and no rate-limit handling (HTTP 429 is just reported as an error).
- No `temperature` / `max_tokens` / `reasoning_effort` tuning — defaults are used, and
  `gpt-oss-120b` emits a long `reasoning` block that is billed as tokens and discarded.
- HTTP status is faked as `200` in the two content-parsing failures, which is misleading
  (§11-I6).
- The API key is interpolated into a header per call — fine, but there is no
  `WebClient`-level default-auth/ logging filter (§11-I11).

---

### 6.11 `service/GroqApiException.java`

Custom `RuntimeException` carrying the HTTP status and raw error body so Groq failures
(invalid key, unknown model, rate limit) are visible instead of a bare
`WebClientResponseException`.

````java
package com.fitness.aiservice.service;

/**
 * Thrown when the Groq API answers with a non-2xx status.
 * Keeps the HTTP status and the raw error body so failures (invalid key,
 * unknown model, rate limit) are visible in the log instead of a bare
 * WebClientResponseException with no payload.
 */
public class GroqApiException extends RuntimeException {

    private final int statusCode;

    public GroqApiException(int statusCode, String responseBody) {
        super("Groq API returned HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
````

---

### 6.12 `service/RecommendationService.java`

Read-only service. **No `save` method exists** — nothing in the module writes
recommendations (§11-I2).

````java
package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;

    public List<Recommendation> getUserRecommendation(String userId) {
        return recommendationRepository.findByUserId(userId);
    }

    public Optional<Recommendation> getActivityRecommendation(String activityId) {
        return recommendationRepository.findByActivityId(activityId);
    }
}
````

---

### 6.13 `controller/RecommendationController.java`

The service's public HTTP surface: two `GET` endpoints under `/api/recommendations`.

````java
package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendation(@PathVariable String userId) {
        // Collection endpoint: an empty list is still a valid (200) response.
        return ResponseEntity.ok(recommendationService.getUserRecommendation(userId));
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId) {
        // Item endpoint: missing resource must be 404, not 500.
        return recommendationService.getActivityRecommendation(activityId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
````

---

### 6.14 `controller/GlobalExceptionHandler.java`

Catches **every** exception and maps it to a 500 with a JSON body. Note: this means a
missing resource, a validation error, and a Groq outage are all reported as `500`
(§11-I5).

````java
package com.fitness.aiservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Converts any unhandled exception into a consistent JSON error response
 * instead of leaking Spring's default error handling.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                ));
    }
}
````

> `Map.of(...)` is immutable and order is non-deterministic — the JSON key order varies
> between requests (§11-I9, cosmetic).
> **Stack traces are not logged** here; only the message reaches the client, which makes
> production debugging harder (§11-I5).

---

### 6.15 `test/.../AiserviceApplicationTests.java`

Default Boot-generated context test. Note it is a full `@SpringBootTest`, so it requires
**MongoDB, RabbitMQ and a resolvable `GROQ_API_KEY`** to pass — it will fail on a machine
without infrastructure (§11-I12).

````java
package com.fitness.aiservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiserviceApplicationTests {

	@Test
	void contextLoads() {
	}

}
````

---

### 6.16 `test/.../service/GroqServiceTest.java`

The only meaningful test in the module. Covers Groq-envelope unwrapping, fence stripping,
and both error paths. **No HTTP is performed** — the service is constructed with a plain
`WebClient.builder()` and only the pure JSON-handling method is exercised.

````java
package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Groq envelope unwrapping. No HTTP is involved: the
 * service is built with a plain WebClient.Builder and only the pure JSON
 * handling method is exercised.
 */
class GroqServiceTest {

    private final GroqService groqService = new GroqService(WebClient.builder(), new ObjectMapper());

    /**
     * Mirrors a real Groq chat-completions response: the answer we care about is
     * nested in choices[0].message.content, surrounded by envelope noise
     * (id/usage/reasoning) that must NOT be returned to the caller.
     */
    @Test
    void extractsOnlyTheAssistantContentFromTheGroqEnvelope() {
        String envelope = """
                {
                  "id": "chatcmpl-479ef8dc-9eba-45cd-afde-ad87d3684536",
                  "object": "chat.completion",
                  "model": "openai/gpt-oss-120b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"analysis\\":{\\"overall\\":\\"Solid run\\"},\\"recommendations\\":[\\"Keep going\\"]}",
                        "reasoning": "User wants a JSON response..."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {"total_tokens": 1254},
                  "x_groq": {"id": "req_01m2vmsngxer7b9cecqewwchb2"}
                }
                """;

        String result = groqService.extractMessageContent(envelope);

        assertEquals("{\"analysis\":{\"overall\":\"Solid run\"},\"recommendations\":[\"Keep going\"]}", result);
        assertFalse(result.contains("chatcmpl"), "envelope id must not leak into the answer");
        assertFalse(result.contains("reasoning"), "reasoning must not leak into the answer");
    }

    /**
     * Defensive path: some models still wrap the object in a ```json fence even
     * when response_format=json_object is requested.
     */
    @Test
    void stripsMarkdownCodeFences() {
        String envelope = """
                {
                  "choices": [
                    { "message": { "content": "```json\\n{\\"analysis\\":{\\"overall\\":\\"ok\\"}}\\n```" } }
                  ]
                }
                """;

        String result = groqService.extractMessageContent(envelope);

        assertEquals("{\"analysis\":{\"overall\":\"ok\"}}", result);
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    void throwsWhenTheEnvelopeHasNoChoicesContent() {
        String envelope = """
                {"id":"chatcmpl-x","choices":[]}
                """;

        GroqApiException ex = assertThrows(GroqApiException.class,
                () -> groqService.extractMessageContent(envelope));

        assertTrue(ex.getMessage().contains("no choices[0].message.content"));
    }

    @Test
    void throwsWhenTheResponseIsNotJson() {
        assertThrows(GroqApiException.class,
                () -> groqService.extractMessageContent("<html>502 Bad Gateway</html>"));
    }
}
````

**Test-coverage gaps:** no tests for `ActivityAIService` (prompt building / the
`distance`/`heartRate` fallbacks), none for `RecommendationService`, none for the
controller, and no integration test of the RabbitMQ listener or the Groq HTTP call
(§11-I13).

---

## 7. The RabbitMQ message contract

### 7.1 Publisher side (`activityservice`, for context)

`ActivityService.trackActivity(...)` saves the entity then publishes the **saved document**
(the Mongo `@Id` is populated at this point):

````java
Activity savedActivity = activityRepository.save(activity);

//publish to rabbitmq for processing
try{
    rabbitTemplate.convertAndSend(exchange,routingKey,savedActivity);
}catch(Exception e){
log.error("Failed to publish activity to RabbitMq:",e);
}
````

The publisher's `Activity` entity (`@Document(collection = "activities")`) looks like:

````java
public class Activity {
    @Id   private String id;
          private String userId;
          private ActivityType type;        // enum: RUNNING, WALKING, CYCLING, SWIMMING,
                                            // WEIGHT_TRAINING, YOGA, CARDIO, STRETCHING, OTHER
          private Integer duration;
          private Integer caloriesBurned;
          private LocalDateTime startTime;

    @Field("metrics")
    private Map<String,Object> additionMetrics;

    @CreatedDate         private LocalDateTime createdAt;
    @LastModifiedDate    private LocalDateTime updatedAt;
}
````

> `@Field("metrics")` renames the field **only in MongoDB**. Jackson — which builds the
> RabbitMQ message — ignores it entirely and emits `additionMetrics`. Verified in §10.4.

### 7.2 Actual wire payload

> **Verified by experiment** (§10.4): the JSON on the wire uses the key
> **`additionMetrics`** (the Java field name), **not** `metrics`. Spring Data's
> `@Field("metrics")` is a **MongoDB-only** annotation — Jackson ignores it — so the
> RabbitMQ message and the Mongo document use **different key names for the same data**.

```json
{
  "id": "6a9412d6ce8026aae194d9e9",
  "userId": "1",
  "type": "RUNNING",
  "duration": 30,
  "caloriesBurned": 300,
  "startTime": "2026-04-09T08:00:00",
  "additionMetrics": {
    "distance": 5.2,
    "averageSpeed": 10.4,
    "maxHeartRate": 165
  },
  "createdAt": "...",
  "updatedAt": "...",
  "_class": "com.fitness.activityservice.model.Activity"
}
```

The **same** activity, as stored in MongoDB, is:

```json
{
  "_id": "6a9412d6ce8026aae194d9e9",
  "userId": "1", "type": "RUNNING", "duration": 30, "caloriesBurned": 300,
  "metrics": { "distance": 5.2, "averageSpeed": 10.4, "maxHeartRate": 165 }
}
```

So there are **two serializations of one entity with two different field names**:

| Hop | Mechanism | Annotation honoured | Key in payload |
|---|---|---|---|
| Mongo write (`fitnessactivity.activities`) | Spring Data Mongo | `@Field("metrics")` | **`metrics`** |
| RabbitMQ publish → AI service | Jackson (`JacksonJsonMessageConverter`) | Jackson only | **`additionMetrics`** |

Because the wire key happens to equal the AI service DTO's Java field name, the mapping
**works today** — but it works *by coincidence of naming*, not by explicit contract.
See §11-I14.

---

### 7.3 The sample request file `act.json` (repo root) — MISLEADING

```json
{"userId":"9fcfb917-665a-4682-97cf-772b7e87a802","type":"RUNNING","duration":30,
 "caloriesBurned":320,"startTime":"2026-04-09T08:00:00",
 "additionalMetrics":{"distanceKm":5.2}}
```

Two problems, both **verified by experiment** (§10.4):

1. **`additionalMetrics` (with the extra `al`) is the name of `ActivityRequest`'s field**
   in `activityservice` — the *REST request DTO*. It is **not** the field on the entity
   (`additionMetrics`) and **not** the AI service's DTO field (`additionMetrics`).
2. **`distanceKm` is never read by anything.** The AI prompt looks for the key **`distance`**.
   The live data uses `distance`; `act.json` uses `distanceKm`.

Result: a request built from `act.json` causes `additionMetrics` to be **`null`** in the AI
service, so the prompt degrades to **Distance: 0.00 km** and **Heart Rate: 0 bpm**
(§11-I1). The file should be corrected to:

```json
{"userId":"...","type":"RUNNING","duration":30,"caloriesBurned":320,
 "startTime":"2026-04-09T08:00:00",
 "additionalMetrics":{"distance":5.2,"averageSpeed":10.4,"maxHeartRate":165}}
```

---

## 8. The LLM prompt and expected output

### 8.1 System prompt

There is **no system role message**. `GroqService` sends a single `user` message containing
the entire prompt built by `ActivityAIService.createPromptForActivity`. The exact current
prompt template is in §6.8 and the fully-rendered request body is:

```json
{
  "model": "openai/gpt-oss-120b",
  "messages": [
    { "role": "user", "content": "Analyze this fitness activity ... Activity Details: ..." }
  ],
  "response_format": { "type": "json_object" }
}
```

### 8.2 Expected response schema

The prompt asks the model to return exactly:

```json
{
  "analysis": {
    "overall": "Overall analysis here",
    "pace": "Pace analysis here",
    "heartRate": "Heart rate analysis here",
    "caloriesBurned": "Calories analysis here"
  },
  "recommendations": ["Recommendation 1", "Recommendation 2"],
  "improvements": [{ "area": "Area name", "recommendation": "Detailed recommendation" }],
  "suggestions": [{ "workout": "Workout name", "description": "Workout description" }]
}
```

### 8.3 Schema mismatch with the persisted entity

| Prompt output field | `Recommendation` entity field | Match? |
|---|---|---|
| `analysis.*` | *(none — only `recommendation: String`)* | ✗ |
| `recommendations[]` | `recommendation: String` | ✗ (array vs string) |
| `improvements[]` | *(none)* | ✗ |
| `suggestions[]` (objects) | `suggestions: List<String>` | ✗ (objects vs strings) |
| *(none)* | `safety: List<String>` | ✗ (never produced by the prompt) |
| *(none)* | `activityId`, `userId`, `activityType` | must be copied from the `Activity` |

**Consequence:** even if code were added to map the LLM JSON onto `Recommendation`, the
mapping would be lossy/incorrect. The entity and the prompt must be reconciled first (§11-I2).

### 8.4 Model-specific notes

- `openai/gpt-oss-120b` is a **reasoning model**: responses include a `reasoning` field next
  to `content`, which is billed as tokens and now discarded. Observed usage for one prompt:
  `prompt_tokens: 243`, `completion_tokens: 1011` (of which `reasoning_tokens: 390`),
  ~2.1 s.
- Because `reasoning_tokens` can dominate cost, tuning `reasoning_effort` (Groq supports
  `low`/`medium`/`high`) or switching model is a candidate optimisation.
- Groq's `json_object` mode requires the prompt to mention JSON — the current prompt does.

---

## 9. REST API surface

Base URL: `http://localhost:8083`

| Method | Path | Request | Success | Failure | Notes |
|---|---|---|---|---|---|
| GET | `/api/recommendations/user/{userId}` | — | `200` `List<Recommendation>` (possibly `[]`) | `500` (generic handler) | **Currently always `[]`** |
| GET | `/api/recommendations/activity/{activityId}` | — | `200` `Recommendation` | `404` if absent | **Currently always `404`** |

There is **no** `POST`/`PUT`/`DELETE`. There is **no** endpoint to trigger a recommendation
on demand, no `GET /api/recommendations/health`, no actuator endpoints, and no authentication
on these routes.

### 9.1 Example responses

`GET /api/recommendations/user/1` →
```json
[]
```

`GET /api/recommendations/activity/6a9412d6ce8026aae194d9e9` →
```
404 Not Found   (empty body)
```

`Recommendation` JSON shape **as currently defined** (this is what *would* be returned if
something ever saved a record):
```json
{
  "id": "...",
  "activityId": "...",
  "userId": "...",
  "activityType": "RUNNING",
  "recommendation": "...",
  "suggestions": ["..."],
  "safety": ["..."],
  "createdAt": "2026-09-19T07:04:24"
}
```

---

## 10. Observed evidence from a live local stack

These are **measurements**, not assumptions. They were taken against the developer's running
environment (MongoDB at `localhost:27017`, services started via the VS Code task
"Start All Services").

### 10.1 The AI call succeeds

Real log line captured while the flow was running:

```
2026-09-19T07:04:24.052+05:30  INFO 33492 --- [ai-service] [ntContainer#0-1]
c.f.a.service.ActivityMessageListener : Generated Recommendation for Activity:
{"id":"chatcmpl-479ef8dc-...","object":"chat.completion","choices":[{"message":
{"content":"```json\n{\n \"analysis\": {...} ...","reasoning":"User wants a JSON
response..."}}],"usage":{...},"x_groq":{...}}
```

Two conclusions drawn from this single line:
1. The listener **does** receive messages and **does** complete a Groq round trip.
2. The logged value was the **whole Groq envelope** and the model's content was
   **markdown-fenced** — both since fixed (see §6.10).

### 10.2 Activity documents exist, but recommendations do not

```
mongosh > db.getSiblingDB('fitnessactivity').activities.countDocuments()
2

mongosh > db.getSiblingDB('fitnessactivity').activities.find(...)
{
  _id: ObjectId('6a9412d6ce8026aae194d9e9'),
  userId: '1', type: 'RUNNING', duration: 30, caloriesBurned: 300,
  metrics: { distance: 5.2, averageSpeed: 10.4, maxHeartRate: 165 }
}

mongosh > db.getSiblingDB('fitnessrecommendation').getCollectionNames()
[ 'recommendations' ]

mongosh > db.getSiblingDB('fitnessrecommendation').recommendations.countDocuments()
0
```

**This is the definitive proof of the core gap:** the destination collection exists (created
by MongoDB because the repository/entity is wired) but is **empty** — the AI output never
reaches storage. Any REST call in §9 therefore returns `[]` / `404`.

### 10.3 Field-key mismatch, confirmed

The published `additionMetrics` payload contains `distance`, `averageSpeed`,
**`maxHeartRate`**. `ActivityAIService` looks up `metrics.get("distance")` ✓ and
`metrics.get("heartRate")` ✗.

So for this data: **distance is passed correctly (5.2 km), heart rate is always 0**, and the
`analysis.heartRate` section the model writes is therefore about a heart rate of 0 bpm — it
typically "helpfully" invents a plausible figure, which is worse than omitting it.

### 10.4 Experiment: how the `metrics` map actually crosses the wire

This was verified by a throwaway JUnit test in the `aiservice` module (since deleted) that
serialized a stand-in for the producer's `Activity` with Jackson and read it back as
`com.fitness.aiservice.model.Activity`. Results:

```
PROBE-A producerJson = {"id":"x","userId":"1","type":"RUNNING","duration":30,
                        "caloriesBurned":300,
                        "additionMetrics":{"distance":5.2,"maxHeartRate":165,"averageSpeed":10.4}}
PROBE-A has 'metrics' key     = false
PROBE-A has 'additionMetrics' = true
PROBE-B additionMetrics = {distance=5.2, maxHeartRate=165, averageSpeed=10.4}
PROBE-B distance        = null     (top-level field is absent on the wire)
PROBE-B heartRate       = null     (top-level field is absent on the wire)
PROBE-C (JSON key = "metrics")  additionMetrics = null
PROBE-D (act.json shape)        additionMetrics = null
```

Conclusions:

| # | Finding | Consequence |
|---|---|---|
| A | Jackson **ignores** Spring Data's `@Field("metrics")`; the wire key is `additionMetrics`. | Mongo and RabbitMQ use different key names for the same field. |
| B | `additionMetrics` **is** populated in the AI service; `distance` / `heartRate` top-level fields stay `null`. | The `additionMetrics` fallback in `ActivityAIService` is doing all the work; the `distance`/`heartRate` fields on the DTO are dead weight. |
| C | If the producer ever emitted the literal key `metrics`, `additionMetrics` would silently become `null`. | Silent data loss with no error — see I14. |
| D | `act.json`'s `additionalMetrics` / `distanceKm` shape yields `additionMetrics == null`. | Requests built from `act.json` produce a metric-less prompt — see I1. |

---

## 11. Known issues and improvement backlog

Ordered by priority. Each item names the file and the concrete problem. **I2, I1 and I3 are
the three that matter most.**

### P0 — correctness / blocking

---

**I2 — The recommendation is never persisted. The service does not do its job.**

- **Where:** `ActivityMessageListener.processActivity` (§6.9), `RecommendationService` (§6.12).
- **Problem:** the AI JSON is logged and thrown away. `RecommendationService` has no `save`
  method. The REST API therefore always returns `[]` / `404` (proven in §10.2).
- **Impact:** the entire service is a no-op from a client's perspective; the REST layer is dead code.
- **Fix direction:** in `processActivity`, deserialize the LLM JSON (e.g. into a new
  `ActivityRecommendation` DTO), map it to the `Recommendation` entity, set
  `activityId`/`userId`/`activityType` from the `Activity`, and call
  `recommendationRepository.save(...)` (add a `saveRecommendation` method to
  `RecommendationService`).
- **Coupling:** must be done together with **I4** — the DTO and the entity schemas do not match.

---

**I1 — Heart rate is always 0; distance breaks for some payloads.**

- **Where:** `ActivityAIService.createPromptForActivity` (§6.8).
- **Problem:** the fallback reads `additionMetrics.get("heartRate")`, but the producer writes
  **`maxHeartRate`** (verified in §10.3/§10.4). `metrics.get("distance")` is correct for the
  live data, but the repo's own sample `act.json` posts `distanceKm`, which would yield `0.0`
  (also verified: §10.4 finding D).
- **Impact:** the LLM is told heart rate is `0 bpm` and (for `act.json`-shaped payloads)
  distance is `0.00 km`. The model then fabricates plausible-sounding analysis. Output is
  confidently wrong — worse than absent.
- **Fix direction:** either (a) model the metrics map explicitly with known keys
  (`distance`, `averageSpeed`, `maxHeartRate`) and read the right ones, or (b) prefer real
  top-level fields and treat the map as a typed record; and/or omit the field from the prompt
  entirely when unknown rather than sending `0`. Add a unit test for both key shapes.

---

**I3 — No timeout on the Groq call; a hung request stalls the consumer forever.**

- **Where:** `GroqService.getAnswer`, the `.block()` call (§6.10).
- **Problem:** no `responseTimeout`, no `connectTimeout`. `.block()` with no argument waits
  indefinitely.
- **Impact:** one stuck HTTP connection permanently consumes a
  `SimpleMessageListenerContainer` thread (default 1 consumer) → the whole service silently
  stops processing activities.
- **Fix direction:** `.timeout(Duration.ofSeconds(N))` on the `Mono` **and** an
  `HttpClient`/`ReactorClientHttpConnector` with connect + response timeouts configured in
  `WebClientConfig`; consider raising `spring.rabbitmq.listener.simple.concurrency`.

---

**I4 — The LLM output schema does not match the `Recommendation` entity.**

- **Where:** §8.3 table; `Recommendation` (§6.6) vs the prompt (§6.8).
- **Problem:** the prompt yields `analysis{}` + `recommendations[]` + `improvements[]` +
  `suggestions[{workout,description}]`; the entity offers `recommendation: String`,
  `suggestions: List<String>`, `safety: List<String>` (never produced).
- **Impact:** no correct mapping exists; any mapping written today is lossy.
- **Fix direction:** pick one canonical schema. Recommended: introduce nested DTOs
  (`Analysis`, `Improvement`, `Suggestion`) and align the entity fields; drop `safety` or add
  it to the prompt. Then map with Jackson
  (`objectMapper.readValue(json, ActivityRecommendation.class)`).

---

**I5 — Poison messages will retry forever and burn money.**

- **Where:** `ActivityMessageListener` (§6.9) + no RabbitMQ listener error config in
  `application.yml` (§5.1).
- **Problem:** with Spring AMQP defaults, an exception thrown from a `@RabbitListener` causes
  **requeue (infinite redelivery)**. The listener calls a paid external API, so a single bad
  message = endless Groq calls and an endless log loop.
- **Impact:** cost blow-up and a stuck queue; a legitimate Groq outage would also loop.
- **Fix direction:** configure `spring.rabbitmq.listener.simple.retry.*` with `max-attempts`
  and `default-requeue-rejected=false`, and declare a dead-letter exchange/queue (DLQ).
  Ensure `GroqApiException` for 4xx (bad key/model) is not retried. Critically: the listener
  currently lets `GroqApiException` escape, which is exactly what triggers the loop.

---

### P1 — robustness / API quality

---

**I6 — `GroqApiException` reports a fake HTTP 200 for parse failures.**

- **Where:** `GroqService.extractMessageContent` (§6.10).
- **Problem:** both failures pass `statusCode = 200`, so the exception message reads
  `"Groq API returned HTTP 200: ..."`. Misleading in logs and impossible to branch on.
- **Fix direction:** use a distinct sentinel (e.g. `-1`) or a dedicated
  `MalformedGroqResponseException`; keep `GroqApiException` for real HTTP errors only.

---

**I7 — `Activity.type` is a `String`, losing type safety and enum parity.**

- **Where:** `model/Activity.java` (§6.5).
- **Problem:** the producer uses an `ActivityType` enum; the consumer uses `String` and
  accepts anything, including typos, silently.
- **Fix direction:** **do not** import the other module's class (that would couple the
  services); define a local `ActivityType` enum mirror in `aiservice` so deserialization
  fails loudly on unknown values, and the prompt uses a controlled vocabulary.

---

**I8 — Lombok constructor/visibility gaps on entities and DTOs.**

- **Where:** `Recommendation` (no `@NoArgsConstructor`/`@AllArgsConstructor`) (§6.6);
  `Activity` relies on `@Data`'s implicit constructor (§6.5).
- **Problem:** `@Data` + `@Builder` removes the implicit no-args constructor; Mongo/Jackson
  behaviour then depends on subtle Lombok rules and can break on refactor or Boot upgrade.
- **Fix direction:** annotate explicitly (`@NoArgsConstructor @AllArgsConstructor @Builder`)
  as `activityservice.model.Activity` already does — follow that existing convention.

---

**I9 — Dead code and unused imports.**

- **Where:** `RabbitMqConfig` defines a `RabbitTemplate` the AI service never uses (§6.3);
  `ActivityAIService` imports `Recommendation` unused (§6.8); `ActivityMessageListener`
  imports `lombok.Value` unused (§6.9); `GlobalExceptionHandler` uses unordered `Map.of` (§6.14).
- **Impact:** noise; the unused `RabbitTemplate` suggests a publisher exists when it doesn't.
- **Fix direction:** delete the unused bean/imports; use an ordered map or a small record for
  the error body.

---

**I10 — Broker topology is declared twice and the queue name is hard-coded.**

- **Where:** `RabbitMqConfig` (§6.3) duplicated in `activityservice` (§7.1);
  `@RabbitListener(queues = "activity.queue")` (§6.9) instead of
  `queues = "${rabbitmq.queue.name}"`.
- **Impact:** renaming the queue in YAML silently breaks consumption; two services owning the
  same declarations invites drift.
- **Fix direction:** use `queues = "${rabbitmq.queue.name}"` at minimum; ideally let one
  service own provisioning and have the other only bind.

---

**I11 — No observability.**

- **Where:** whole module.
- **Problem:** no actuator/health, no metrics (Groq latency, token usage, failure count), no
  correlation id linking an `activityId` across the RabbitMQ hop and the HTTP call. Token
  usage is discarded even though Groq returns it in `usage`.
- **Fix direction:** add `spring-boot-starter-actuator`, publish Micrometer metrics
  (timer + counter around the Groq call), and log the Groq `usage`/`id` fields at DEBUG.

---

**I12 — The context test needs live infrastructure.**

- **Where:** `AiserviceApplicationTests` (§6.15).
- **Problem:** `@SpringBootTest` boots the real context → requires MongoDB, RabbitMQ and a
  resolvable `GROQ_API_KEY`. It fails offline / in CI without services.
- **Fix direction:** add a `test` profile with mocked Mongo/Rabbit, or narrow it to a slice test.

---

### P2 — polish / performance

---

**I13 — Thin test coverage.**

- **Where:** `src/test` (§6.15–§6.16).
- **Problem:** only the Groq JSON parsing is tested. `ActivityAIService` prompt building,
  the metrics fallbacks, `RecommendationService`, and the controller are untested; there is
  no integration test proving "message in → recommendation in Mongo".
- **Fix direction:** unit-test `ActivityAIService` (constructor-inject a mock `GroqService`),
  add a `@WebMvcTest` for the controller, and add an end-to-end test once I2 is implemented.

---

**I14 — The `additionMetrics` wire contract is implicit and Mongo/RabbitMQ disagree.**

- **Where:** `model/Activity.additionMetrics` (§6.5) vs the publisher's
  `@Field("metrics") private Map<String,Object> additionMetrics` (§7.1).
- **Problem (verified, §10.4):** Spring Data's `@Field("metrics")` is honoured **only by the
  Mongo mapping layer**. The RabbitMQ message is produced by Jackson, which ignores `@Field`
  and emits the **Java field name** `additionMetrics`. So the same field is called `metrics`
  in Mongo and `additionMetrics` on the wire. The AI service's DTO happens to use the same
  Java name, so it works — **by coincidence, with no explicit contract on either side.**
  Renaming the producer's Java field (a refactor that looks harmless, since `@Field` keeps
  the Mongo document stable) would silently set `additionMetrics` to `null` on the consumer,
  and the prompt would silently degrade to `0` for distance and heart rate.
- **Fix direction:** make the wire contract explicit:
  1. On the producer's entity, pin the JSON name: `@JsonProperty("additionMetrics")` (or add
     `@JsonIgnore` on anything not intended for the wire).
  2. On the AI service's DTO, add the matching explicit
     `@JsonProperty("additionMetrics")` so the dependency is visible and refactor-safe.
  3. **Do not** add `@JsonProperty("metrics")` to the AI DTO — that would *break* the
     currently-working path (§10.4 finding C).
  4. Add a consumer-side test that asserts `additionMetrics` is non-null for a representative
     producer payload.
  5. Consider posting the same JSON document that is stored in Mongo (via Spring Data's
     converter) so both hops share one canonical representation.

---

**I15 — Prompt/LLM tuning opportunities (cost and quality).**

- **Where:** `ActivityAIService` prompt (§6.8) + `GroqService` request body (§6.10).
- **Problem:** one free-form `user` message; no system prompt, no few-shot example, no
  `temperature`, no `max_tokens`, no `reasoning_effort`; `gpt-oss-120b` reasoning tokens are
  paid for and thrown away (390 of 1011 completion tokens in the observed sample).
- **Fix direction:** move the schema/behaviour rules into a `system` message, set
  `reasoning_effort=low` (or switch to a non-reasoning model) and `max_tokens`; consider
  making the schema configurable per activity type (`RUNNING` needs pace analysis,
  `YOGA`/`WEIGHT_TRAINING` do not).

---

**I16 — The async contract is undocumented for clients.**

- **Where:** repo docs.
- **Problem:** the activity API's `200 OK` implies the response is complete, but the
  recommendation arrives later (or never). Nothing tells the client to poll
  `/api/recommendations/...`.
- **Fix direction:** document the flow (this file §3 is a start) and consider `202 Accepted`
  plus polling or SSE.

---

## 12. Build, run and test

### 12.1 Prerequisites

- JDK 21, Maven (or the bundled `mvnw`)
- MongoDB on `localhost:27017`
- RabbitMQ on `localhost:5672` (guest/guest)
- A `.env` in the repo root containing `GROQ_API_KEY=...` (copy `.env.example`)

### 12.2 Commands

```powershell
# from the aiservice folder
.\mvnw.cmd -DskipTests compile          # compile
.\mvnw.cmd -DskipTests package          # build the executable JAR
.\mvnw.cmd spring-boot:run              # run on port 8083
.\mvnw.cmd -Dtest=GroqServiceTest test  # run the unit tests
.\mvnw.cmd test                         # full test run (needs Mongo+Rabbit+key)
```

Or from the repo root, the VS Code task **"Start All Services"** starts
Eureka → User → Activity → AI (`cd <module> && mvn spring-boot:run`).

Whole-project build from the root aggregator:

```powershell
mvn -DskipTests package      # builds eureka, userservice, activityservice, aiservice
```

### 12.3 Manual end-to-end smoke test

```powershell
# 1. ensure a user exists (userservice, port 8081)
# 2. track an activity (activityservice, port 8082)
curl.exe -X POST http://localhost:8082/api/activities `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1" `
  -d '{\"userId\":\"1\",\"type\":\"RUNNING\",\"duration\":30,\"caloriesBurned\":300,
       \"startTime\":\"2026-04-09T08:00:00\",
       \"additionalMetrics\":{\"distance\":5.2,\"averageSpeed\":10.4,\"maxHeartRate\":165}}'

# 3. watch the aiservice console -- the Groq response is logged there
# 4. check what (if anything) was persisted
mongosh --quiet --eval "db.getSiblingDB('fitnessrecommendation').recommendations.countDocuments()"
```

> **Field names through the hops (all three differ!):**
> the REST body uses `additionalMetrics` (field of `ActivityRequest`), the entity stores it as
> `metrics` in Mongo (via `@Field`), but the RabbitMQ message carries `additionMetrics`
> (Jackson uses the Java field name). Use the keys `distance` / `maxHeartRate` inside the map —
> **not** `distanceKm` from `act.json` — or the prompt will show `0.00 km` / `0 bpm` (§7.3, §10.4).

### 12.4 Verification status of the current code

```
mvn -DskipTests compile   ->  BUILD SUCCESS

mvn -Dtest=GroqServiceTest test
    -> Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
    -> BUILD SUCCESS
```

---

## 13. Constraints any improvement must respect

1. **Do not break the message contract.** `activityservice` is the producer; changing
   `aiservice`'s `Activity` DTO field names/annotations silently breaks deserialization
   (see I14). Prefer explicit `@JsonProperty` mappings.
2. **Do not add cross-module Java dependencies.** The modules are separate Maven projects
   with no shared artifact (no common `-dto` module exists). Duplicating small DTOs/enums
   (I7) is the current, intentional convention — introducing a shared module is a valid
   larger refactor but changes the build layout.
3. **Keep Spring Boot 4 / Java 21 idioms.** Boot 4 renamed starters (`-webmvc`), and
   `spring-boot-starter-test` is replaced by the `-webmvc-test` / `-data-mongodb-test`
   starters used here. Do not "fix" the POM back to Boot 3 names.
4. **The app is servlet-based.** `WebClient` is used reactively-but-blocked (`.block()`).
   Either keep that pattern consistently or migrate properly — do not mix.
5. **Secrets stay in `.env`.** `GROQ_API_KEY` must never be hard-coded or committed;
   `.env.example` documents the variable.
6. **Two halves of the repo are coupled.** When the payload or queue changes,
   `activityservice` (publisher) and `aiservice` (consumer) must be changed in a
   coordinated way.

---

## 14. Quick reference: file → responsibility → state

| # | File | Responsibility | State |
|---|---|---|---|
| 1 | `AiserviceApplication` | Boot entry | OK |
| 2 | `config/MongoConfig` | `@EnableMongoAuditing` | OK |
| 3 | `config/RabbitMqConfig` | Queue/exchange/binding + converter | duplicated, unused `RabbitTemplate` (I9/I10) |
| 4 | `config/WebClientConfig` | `WebClient.Builder` bean | no timeouts (I3) |
| 5 | `model/Activity` | Inbound DTO | implicit `metrics` mapping, `String type` (I7/I14) |
| 6 | `model/Recommendation` | Mongo entity | **schema mismatch (I4)**, constructor gaps (I8) |
| 7 | `repository/RecommendationRepository` | Mongo access | unused because nothing saves (I2) |
| 8 | `service/ActivityAIService` | Prompt building | **wrong/absent metric keys (I1)**, unused import (I9) |
| 9 | `service/ActivityMessageListener` | RabbitMQ consumer | **logs only, no persistence (I2)**, no error handling (I5) |
| 10 | `service/GroqService` | LLM HTTP client | works; no timeout/retry (I3/I6) |
| 11 | `service/GroqApiException` | Error type | fake HTTP 200 (I6) |
| 12 | `service/RecommendationService` | Read API | **no save method (I2)** |
| 13 | `controller/RecommendationController` | REST reads | correct, but always empty (I2) |
| 14 | `controller/GlobalExceptionHandler` | Error mapping | all errors → 500, no stack trace logged (I5) |
| 15 | `test/AiserviceApplicationTests` | Context smoke test | needs live infra (I12) |
| 16 | `test/service/GroqServiceTest` | Groq JSON unit tests | passing (4 tests) |

---

## 15. Bottom line for a reviewer

The **LLM integration is healthy** after the recent Groq-envelope fix: the service now sends
`response_format=json_object`, unwraps `choices[0].message.content`, strips markdown fences,
and is unit-tested.

The service is nevertheless **architecturally incomplete**, and the following four items
should be addressed in order before anything else:

1. **I2 + I4** — map the LLM JSON into `Recommendation` and persist it, which requires
   reconciling the prompt schema with the entity (or introducing matching DTOs).
2. **I1** — read `maxHeartRate` (and a consistent distance key) instead of `heartRate`,
   and stop sending `0` when a metric is unknown.
3. **I3** — add connect/response timeouts so one hung HTTP call cannot stall the consumer.
4. **I5** — add retry limits + a dead-letter queue so a bad message cannot loop forever
   against a paid API.





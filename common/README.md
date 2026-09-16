# Common Module

Shared, domain-free cross-cutting infrastructure library for the distributed microservices platform.

---

## Features

- **Correlation ID Tracking**:
  - `CorrelationIdFilter`: Servlet filter that extracts or generates `X-Correlation-Id` header and sets it in SLF4J MDC (`correlationId`) for log aggregation and downstream propagation.
  - `LoggingAutoConfiguration`: Spring Boot auto-configuration automatically loaded via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **Consistent Error Responses**:
  - `ErrorResponse` (`com.distributedplatform.common.web`): a one-field `record ErrorResponse(String error)` returned by every service's `@RestControllerAdvice` exception handlers, so all error bodies share the same JSON shape (`{"error": "..."}`) instead of each service inventing its own.

---

## Usage in Services

Add the dependency to any service's `build.gradle`:

```groovy
dependencies {
    implementation project(':common')
}
```

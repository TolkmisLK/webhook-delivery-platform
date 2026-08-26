# Changelog / 变更记录

All notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic versioning.

重要变更遵循 Keep a Changelog 与语义化版本规范。

## [Unreleased]

### Added / 新增

- Race-safe, idempotent cancellation for queued delivery jobs with a stable conflict response.
- Bilingual cancellation controls and bounded Prometheus coverage for the `CANCELED` state.
- Commit-consistent delivery SSE notifications and after-commit operator action logs.
- Immutable target URL and encrypted signing-secret snapshots for every accepted delivery.
- Versioned Endpoint signing-secret rotation with metadata-only after-commit audit logs.
- Bilingual secret-rotation controls and old/new delivery-signature integration coverage.

## [0.3.0] - 2026-08-25

### Added / 新增

- Versioned Endpoint activation and deactivation with stable HTTP 409 conflict responses.
- Bilingual operator controls that publish events only to active Endpoints.
- After-commit structured lifecycle logs for Endpoint status changes.
- Prometheus exposition with bounded delivery-status gauges and oldest-runnable-job age.
- Reproducible, localhost-bound Prometheus Compose profile.

## [0.2.0] - 2026-08-25

### Added / 新增

- Delivery detail API and bilingual committed-attempt timeline.
- After-commit Micrometer counters, duration timers, and structured completion logs.
- Controlled transient-failure receiver and retry-recovery integration coverage.

## [0.1.0] - 2026-08-25

### Added / 新增

- Java 21 and Spring Boot 4.1 modular backend.
- PostgreSQL event and delivery-job persistence with Flyway migrations.
- HMAC-SHA256 delivery, AES-256-GCM secret storage, retries, leases, and dead letters.
- React and TypeScript operations console with SSE updates.
- Docker Compose demo receiver, automated tests, CI, architecture, API, and security documentation.
- Spring Modulith dependency-graph verification and Google Java Format enforcement.

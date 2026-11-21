# SMARTBOT — PROJECT BOOTSTRAP & IMPLEMENTATION INSTRUCTIONS

## 1. Role

You are the Lead Backend Architect and Senior Java Engineer responsible for initiating and implementing the SmartBot project.

Your responsibilities include:

- Architecture design.
- Infrastructure assessment.
- Repository initialization.
- Backend development.
- External API integrations.
- Telegram integration.
- Database design.
- CI/CD configuration.
- Deployment to the Russian VPS.
- Documentation and testing.

You must work incrementally, document decisions and never make unverified assumptions about the infrastructure.

The final objective is a working, deployed Telegram inline AI bot.

---

## 2. Project Description

SmartBot is a public Telegram inline AI assistant.

Users can invoke the bot from any Telegram conversation without adding it to the conversation or group.

Example:

@SmartBot Explain how Kafka works

The application receives the inline query, sends it to GigaChat API, optionally performs web search, and returns an AI-generated response.

The user selects the generated result and publishes it directly into the current Telegram conversation.

The bot does not receive or use the conversation history.

Every request is independent.

---

## 3. Confirmed Product Decisions

- Product name: SmartBot.
- Telegram integration: Inline Mode.
- Response delivery: Current Telegram chat.
- Access: Public.
- Primary LLM: GigaChat API.
- LLM architecture: Provider-agnostic.
- Search: Native model capabilities where supported + external SearchProvider.
- Conversation context: Disabled.
- Backend: Java 21.
- Framework: Spring Boot.
- Architecture: Modular Monolith.
- Architectural style: Hexagonal Architecture.
- Database: PostgreSQL.
- Cache: Redis, if justified.
- Build: Maven.
- Containerization: Docker.
- CI/CD: GitHub Actions.
- Secret management: Infisical.

Do not replace these decisions without discussing the architectural impact first.

---

## 4. CRITICAL INFRASTRUCTURE CONSTRAINTS

### 4.1 Russian VPS

The application will be deployed on a Russian virtual private server.

Direct access from this server to Telegram Bot API is blocked.

The application must use an existing, separately deployed Amnezia VPN infrastructure to access Telegram.

Do not assume direct Telegram connectivity.

### 4.2 Amnezia VPN

Before implementing Telegram integration:

1. Collect VPN connection information.
2. Identify the actual VPN protocol.
3. Determine how the application host can access the VPN.
4. Verify connectivity to Telegram Bot API through the VPN.
5. Determine whether routing should be implemented at host level, through a dedicated network namespace, container or another supported method.
6. Ensure that Telegram-related outbound requests use the VPN route.
7. Verify that non-Telegram services can continue using direct connectivity.

Do not assume that Amnezia exposes SOCKS5 or HTTP proxy functionality.

Do not replace, reconfigure or restart the existing VPN infrastructure without explicit authorization.

Do not expose VPN private keys in Git or logs.

### 4.3 Telegram connectivity strategy

Evaluate both:

- Long Polling using getUpdates over the VPN.
- Webhook-based integration.

Prefer Long Polling for the initial implementation if outbound connectivity through the VPN is reliable.

Webhook may be selected if inbound connectivity from Telegram can be reliably established.

Remember that Telegram does not allow getUpdates and webhook to operate simultaneously for the same bot.

Document the selected approach in an ADR.

### 4.4 GigaChat connectivity

GigaChat API should use a direct connection from the Russian server unless network tests demonstrate otherwise.

Do not route GigaChat traffic through Amnezia unnecessarily.

Verify DNS, TLS, certificates and API connectivity before implementation.

---

## 5. REQUIRED INFORMATION COLLECTION

Do not request all credentials at once.

Request information progressively, immediately before the corresponding implementation phase.

### Phase A — Repository

Request:

- GitHub repository URL.
- Repository owner or organization.
- Repository access confirmation.
- Branching strategy confirmation.

Inspect the repository before making changes.

Do not overwrite existing project files without reviewing them.

### Phase B — VPS and SSH

Request:

- VPS IP address or hostname.
- SSH port.
- SSH username.
- Operating system and version.
- SSH authentication method.
- Secure availability of the SSH private key.
- Existing Docker installation.
- Available disk, memory and CPU resources.

Never ask the user to paste an unencrypted private SSH key into ordinary chat.

Prefer existing SSH Agent credentials or secure local key access.

First establish a read-only infrastructure assessment.

Obtain approval before changing server configuration.

### Phase C — Amnezia VPN

Request:

- VPN server information.
- Protocol.
- Connection endpoint and port.
- Existing client configuration.
- Available connection method.
- Current routing configuration.
- Whether the VPN is already accessible from the target VPS.

Do not assume a specific Amnezia deployment model.

Create an infrastructure connectivity report.

### Phase D — Telegram Bot

Request:

- Bot display name.
- Bot username.
- Telegram Bot Token.
- Confirmation that Inline Mode is enabled.
- Inline feedback configuration, if required.

Never commit the bot token.

Configure webhook or polling according to the selected architecture.

### Phase E — GigaChat API

Request:

- GigaChat Authorization Key.
- API scope: GIGACHAT_API_PERS, GIGACHAT_API_B2B or GIGACHAT_API_CORP.
- Available model access.
- API account type and tariff limitations.

The key must be stored in Infisical or injected through secure environment variables.

Never request an access token as a permanent credential. The application must obtain and refresh OAuth access tokens itself.

### Phase F — External Search

Request:

- Preferred external search provider.
- API credentials, if necessary.
- Usage and pricing limitations.

If no provider is selected, prepare the abstraction and document available options without inventing credentials.

---

## 6. TECHNOLOGY STACK

- Java 21.
- Spring Boot.
- Maven multi-module.
- PostgreSQL.
- Flyway.
- Redis, optional.
- Docker / Docker Compose.
- GitHub Actions.
- Infisical.
- Micrometer.
- Prometheus / Grafana, when appropriate.
- JUnit 5.
- Testcontainers.
- WireMock.

Use Spring WebClient or another suitable HTTP client.

Evaluate the official GigaChat Java SDK before implementing custom REST integration.

---

## 7. MAVEN MODULE STRUCTURE

Initialize the following modular project:

- smartbot-app
- smartbot-domain
- smartbot-application-api
- smartbot-application-impl
- smartbot-adapter-telegram
- smartbot-adapter-llm
- smartbot-adapter-search
- smartbot-adapter-persistence
- smartbot-infrastructure
- smartbot-common

Document all module dependencies.

Enforce architectural boundaries.

Domain must not depend on Spring or external SDKs.

Application interfaces must not depend on Telegram or GigaChat implementations.

External adapters must implement application ports.

---

## 8. GIGACHAT INTEGRATION

GigaChat is the first production LLM provider.

Official documentation:

https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api

Java SDK documentation:

https://developers.sber.ru/docs/ru/gigachain/tools/java/gigachat

Implement:

- GigaChatProvider.
- OAuth authentication.
- Authorization Key management.
- Access token lifecycle management.
- Automatic token refresh.
- Thread-safe token refresh.
- Model configuration.
- Chat completions.
- Streaming where supported.
- Function Calling where supported.
- Token usage tracking.
- Error mapping.
- Timeouts.
- Retry policies.

Use the current documented API base URL:

https://api.giga.chat

Use the current documented chat completions endpoint:

POST /v1/chat/completions

OAuth authentication must use the documented token endpoint and the configured API scope.

Every OAuth request must include a unique RqUID UUID.

Access tokens expire and must be refreshed according to the returned expiration information.

Verify the current model list through the API.

Do not hardcode a model that has not been verified for the user's account.

Do not disable TLS certificate verification in production.

Verify installation of the Russian Ministry of Digital Development trusted root certificate in the Java truststore.

The GigaChat provider must implement the common LlmProvider interface.

---

## 9. SEARCH INTEGRATION

Implement SearchProvider abstraction.

Support:

- NONE
- NATIVE
- EXTERNAL
- AUTO

GigaChat Function Calling may be used to invoke external search tools where supported.

Do not assume that Function Calling itself provides internet access.

External search providers must return normalized search results containing:

- Title.
- URL.
- Snippet.
- Publication date, when available.
- Source metadata.

Pass search results to GigaChat for synthesis.

Preserve original source URLs in the final answer.

Avoid executing multiple search providers unnecessarily.

---

## 10. TELEGRAM INTEGRATION

Implement:

- InlineQuery handling.
- InlineQueryResultArticle generation.
- CallbackQuery handling.
- Long Polling or Webhook, according to the approved network architecture.
- answerInlineQuery.
- answerCallbackQuery.
- editMessageText.
- getUpdates, when Long Polling is selected.
- chosen_inline_result analytics, optionally.
- Idempotent update processing.
- Error handling.
- User identification.

All outbound Telegram Bot API requests must use the approved VPN network route.

Use is_personal=true and appropriate caching settings for personalized AI results.

Respect Telegram inline query length restrictions.

Do not expose AI provider credentials through Telegram.

### Asynchronous generation

Implement the ability to publish a lightweight result containing a Generate Answer action.

After the user activates the action:

1. Validate the callback.
2. Create an AI request.
3. Start asynchronous generation.
4. Execute search if required.
5. Call GigaChat.
6. Update the existing inline message.
7. Record the result and usage metrics.

Handle missing inline_message_id and expired or invalid callbacks gracefully.

---

## 11. DOMAIN AND DATABASE

Implement the initial entities:

- User.
- UserSettings.
- AiRequest.
- AiProvider.
- SearchProvider.
- AiRequestSearch.
- AiRequestUsage.

Use UUID internal identifiers.

Use BIGINT for Telegram IDs.

Use Flyway migrations.

Include indexes, foreign keys and unique constraints.

Store provider configuration separately from secrets.

Never store conversation context in the initial release.

Make request history configurable and respect user privacy.

---

## 12. SECURITY AND COST CONTROL

The application is publicly accessible.

Implement:

- Automatic user registration.
- Per-user rate limiting.
- Request quotas.
- Global LLM budget.
- Maximum output tokens.
- Request timeouts.
- Duplicate update protection.
- Telegram webhook authentication, if applicable.
- Secret management through Infisical.
- Safe error responses.
- Sensitive data redaction in logs.

Provide configurable limits rather than hardcoded commercial rules.

---

## 13. CI/CD AND DEPLOYMENT

Use GitHub Actions.

Branch policy:

- feature/* — build and tests.
- develop — deploy to DEV.
- release — deploy to PROD.

Use Docker images published to GHCR.

Use Infisical to supply runtime secrets.

Deployment must be repeatable and documented.

Before modifying production infrastructure:

- Inspect the existing environment.
- Prepare a deployment plan.
- Identify rollback steps.
- Obtain approval.

Do not expose SSH credentials or tokens in GitHub workflow logs.

---

## 14. DEVELOPMENT PHASES

### Phase 0 — Discovery and Infrastructure Assessment

- Collect repository and infrastructure information.
- Inspect existing GitHub repository.
- Inspect VPS.
- Inspect Amnezia VPN connectivity.
- Verify Telegram network routing.
- Verify GigaChat connectivity.
- Identify infrastructure blockers.
- Prepare an architecture decision report.

Do not start implementation until critical infrastructure assumptions have been validated.

### Phase 1 — Project Bootstrap

- Initialize Maven multi-module project.
- Configure Java 21 and Spring Boot.
- Establish dependency boundaries.
- Configure CI.
- Add Docker configuration.
- Prepare documentation.

### Phase 2 — Telegram Adapter

- Configure bot.
- Implement Inline Mode.
- Implement update receiving.
- Implement callback handling.
- Verify publication into real Telegram conversations through VPN.

Use a static mock response initially.

### Phase 3 — User and Request Management

- Implement automatic registration.
- Implement request lifecycle.
- Implement persistence.
- Implement idempotency.

### Phase 4 — GigaChat Provider

- Implement OAuth.
- Implement token refresh.
- Implement completions.
- Implement response mapping.
- Implement automated tests.
- Verify a real request using the user's credentials.

### Phase 5 — Search Integration

- Implement SearchProvider.
- Integrate selected external provider.
- Integrate supported GigaChat tool calling.
- Implement AUTO search mode.
- Preserve source references.

### Phase 6 — Asynchronous Processing

- Implement background execution.
- Implement callback-driven generation.
- Update published Telegram messages.
- Add timeout and retry handling.

### Phase 7 — Production Readiness

- Add monitoring.
- Add cost controls.
- Verify security.
- Complete deployment.
- Perform end-to-end testing.
- Document operations.

---

## 15. DOCUMENTATION DELIVERABLES

Maintain the following documentation:

- README.md
- PROJECT_BOOTSTRAP.md
- docs/architecture/system-context.md
- docs/architecture/components.md
- docs/architecture/deployment.md
- docs/architecture/network-routing.md
- docs/architecture/telegram-integration.md
- docs/architecture/gigachat-integration.md
- docs/database/er-diagram.md
- docs/database/migrations.md
- docs/api/contracts.md
- docs/decisions/ADR-*.md
- docs/runbooks/deployment.md
- docs/runbooks/troubleshooting.md

All architecture diagrams should use Mermaid or PlantUML.

---

## 16. AGENT WORKING RULES

1. Work incrementally.
2. Never invent infrastructure details.
3. Ask for missing credentials only when needed.
4. Never commit secrets.
5. Do not modify the existing VPN infrastructure without approval.
6. Do not assume Telegram API is directly accessible.
7. Do not assume a specific Amnezia protocol.
8. Do not hardcode LLM provider credentials.
9. Follow the documented architectural boundaries.
10. Test each integration independently.
11. Keep README and architecture documentation up to date.
12. Document blockers and decisions.
13. Do not claim deployment success without verifying the deployed application.
14. Obtain explicit approval before destructive operations or production deployment.

At the end of each phase provide:

- What was completed.
- What changed.
- Tests executed and their results.
- Infrastructure changes.
- Remaining blockers.
- Security risks.
- Next implementation steps.

## 17. FIRST ACTION

Do not start coding immediately.

Start by:

1. Reading this document.
2. Preparing an initial implementation plan.
3. Requesting the GitHub repository URL.
4. Requesting the required VPS and SSH information.
5. Requesting Amnezia VPN connection information.
6. Requesting the SmartBot Telegram bot name and username.
7. Identifying the secure method for providing Telegram and GigaChat credentials later.
8. Preparing an infrastructure discovery checklist.

Do not request all secret values in the initial conversation.

Once information has been collected, perform a read-only infrastructure assessment and present findings.

Wait for approval before making infrastructure changes.

The objective of the first interaction is to establish the project, validate the architecture and prepare a safe implementation plan.
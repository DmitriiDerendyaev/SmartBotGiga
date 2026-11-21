-- SmartBot baseline migration (Phase 1).
-- Доменные таблицы (users, user_settings, ai_requests, ai_providers,
-- search_providers, ai_request_searches, ai_request_usage) добавляются в Phase 3.

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid() для UUID-идентификаторов

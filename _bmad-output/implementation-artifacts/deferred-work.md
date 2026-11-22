# Deferred Work

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-safe-errors-redaction.md`
  summary: AD-20 port-shape страж — user-facing контракты (порты/DTO ответов) должны принимать ErrorCode, а не свободный String; проверить форму контрактов, когда они появятся.
  evidence: Blind Hunter: текущий guard запрещает только вызовы getMessage/getLocalizedMessage/toString в адаптерах; shape-проверка невозможна, пока user-facing порты не существуют (stories 1.4–1.6). Реализовать в story 1.4/1.6 вместе с контрактами.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-safe-errors-redaction.md`
  summary: ErrorCode.fromName/tryParse + политика неизвестных/legacy кодов для персистентности.
  evidence: Blind Hunter: javadoc обещает хранение по имени, но valueOf падает на неизвестных именах при эволюции каталога; нужно в story 1.3 (миграция V3, error_code varchar).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-safe-errors-redaction.md`
  summary: DomainException — опция без stack trace для control-flow кодов (VALIDATION, RATE_LIMITED, EXPIRED).
  evidence: Blind Hunter: fillInStackTrace на каждом ожидаемом отказе — стоимость под нагрузкой; оптимизация, когда появится нагрузка (Epic 4).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-safe-errors-redaction.md`
  summary: SecretMasker hot-path pre-check (дешёвый indexOf-скан перед regex-проходами).
  evidence: Blind Hunter: 5 regex-проходов на каждое событие, включая чистые; оптимизация по профилированию, не сейчас.

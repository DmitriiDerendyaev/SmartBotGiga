# Network Routing — SmartBot

> Статус: ⏳ ожидает Phase 0 (read-only infrastructure assessment).
> НИКАКИЕ предположения о протоколе Amnezia не делаются до проверки.

## Требования (initSpec.md §4)

1. Российский VPS: прямой доступ к Telegram Bot API **заблокирован**.
2. Весь Telegram-трафик (входящий и исходящий) — через существующую
   **Amnezia VPN** инфраструктуру.
3. GigaChat API — **напрямую**, без VPN (если тесты не покажут иное).
4. Остальные сервисы (PostgreSQL, Infisical, GHCR, search) — напрямую.
5. Запрещено: перенастройка/рестарт существующего VPN без явного разрешения;
   попадание VPN-ключей в Git и логи.
6. Не предполагать, что Amnezia предоставляет SOCKS5/HTTP proxy.

## Варианты маршрутизации (оцениваются в Phase 0)

| Вариант | Суть | Плюсы | Минусы |
|---|---|---|---|
| A. Host-level routing | VPN-клиент на хосте, policy routing (fwmark/ip rule): только Telegram IP/домены через VPN | просто для приложения | затрагивает хост, нужен approval |
| B. Network namespace | выделенный netns с VPN-интерфейсом, приложение/контейнер в нём | изоляция, остальной хост не трогается | сложнее эксплуатация |
| C. Container sidecar | VPN-клиент в контейнере, app-контейнер использует его network namespace (`network_mode: service:vpn`) | Docker-native, воспроизводимо | зависит от протокола/образа VPN |
| D. Прокси-уровень | если VPN поддерживает proxy-режим — HTTP/SOCKS proxy для WebClient | точечная маршрутизация в коде | **не предполагать**, требует проверки |

Предварительный фаворит: **C (sidecar)** или **B (netns)** — изоляция Telegram-трафика
без изменения хостовой сети. Решение фиксируется в ADR после Phase 0.

## Telegram connectivity: Long Polling vs Webhook

- **Long Polling (`getUpdates`)** — предпочтителен для старта: нужны только
  исходящие соединения через VPN; не требует inbound-доступности и TLS-домена.
- **Webhook** — возможен, если Telegram сможет достучаться до VPS (публичный
  HTTPS, порт 443/8443 и т.д.); требует secret_token и проверки inbound.
- Одновременно `getUpdates` и webhook для одного бота **не работают**.
- Решение → ADR-0002 (после проверки VPN-канала).

## Checklist проверки (Phase 0)

- [ ] Протокол Amnezia установлен фактически (не предполагать)
- [ ] VPN-клиент поднимается на целевом VPS (или рядом)
- [ ] `curl https://api.telegram.org` через VPN-маршрут — успех
- [ ] `curl https://api.giga.chat` напрямую — DNS, TLS, сертификат Минцифры в truststore
- [ ] Не-Telegram трафик не уходит в VPN (split routing проверен)
- [ ] Latency/стабильность VPN-канала достаточны для long polling

## Отчёт

Infrastructure connectivity report будет создан по итогам Phase 0.
**Внимание (AD-12):** чувствительные инфра-факты (IP, порты, SSH usernames,
VPN endpoints) не коммитятся — отчёт хранится в защищённом месте, в Git —
обезличенная версия/ссылка (`phase0-connectivity-report.md` в .gitignore).

## Уточнено Architecture Spine (binding)

- **AD-5 fail-closed:** при падении VPN-канала — unhealthy + алерт, никакого
  тихого fallback на прямой маршрут.
- **AD-5 key delivery:** VPN-ключ — только файлом на хосте
  (`/etc/smartbot/vpn/`, root, 0600, ro-монт в sidecar) или docker secret;
  запрещены env контейнера (виден в `docker inspect`) и запекание в образ.
- **AD-5 privileges:** sidecar — только `NET_ADMIN` (+ `/dev/net/tun` при
  необходимости), без `privileged: true`; образ pinned по digest.
- **AD-23:** offset getUpdates персистится; at-least-once + ledger;
  один poller на bot token во всех окружениях.
- **Phase 0 доп. проверка:** логирует ли VPN-сервер URL (bot token в path!) —
  если да, маскировать с approval (SEC-06).

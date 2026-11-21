# SmartBot — команды разработки через Docker (локальный JDK не требуется).
# Использование: make build | make test | make verify | make image | make up-dev ...

MAVEN_IMAGE := maven:3.9-eclipse-temurin-21
DOCKER_RUN  := docker run --rm \
	-v $(PWD):/workspace \
	-v smartbot-m2:/root/.m2 \
	-w /workspace \
	$(MAVEN_IMAGE)

.PHONY: build test verify package image up-dev down-dev up down logs shell-mvn

## Полная сборка без тестов
build:
	$(DOCKER_RUN) mvn -B -q compile

## Unit-тесты (включая ArchUnit)
test:
	$(DOCKER_RUN) mvn -B test

## Полная проверка: unit + integration tests (Testcontainers требует Docker-in-Docker mount)
verify:
	docker run --rm \
		-v $(PWD):/workspace \
		-v smartbot-m2:/root/.m2 \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-w /workspace \
		$(MAVEN_IMAGE) mvn -B verify

## Сборка jar
package:
	$(DOCKER_RUN) mvn -B -q package -DskipTests

## Docker-образ приложения
image:
	docker build -t ghcr.io/dmitriiderendyaev/smartbot:local .

## Инфраструктура для локальной разработки (PostgreSQL, опц. Redis)
up-dev:
	docker compose -f docker-compose.dev.yml up -d

down-dev:
	docker compose -f docker-compose.dev.yml down

## Полный стек
up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f app

## Произвольная Maven-команда: make shell-mvn CMD="mvn dependency:tree"
shell-mvn:
	$(DOCKER_RUN) $(CMD)

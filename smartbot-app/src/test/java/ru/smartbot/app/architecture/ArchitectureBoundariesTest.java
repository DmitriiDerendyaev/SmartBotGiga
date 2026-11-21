package ru.smartbot.app.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforcement архитектурных границ Modular Monolith / Hexagonal Architecture.
 * Правила соответствуют initSpec.md §7 и docs/architecture/components.md.
 */
class ArchitectureBoundariesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ru.smartbot");
    }

    @Test
    @DisplayName("Domain не зависит от Spring, адаптеров и внешних SDK")
    void domainIsPure() {
        ArchRule rule = noClasses().that().resideInAPackage("ru.smartbot.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "ru.smartbot.adapter..",
                        "ru.smartbot.application.impl..",
                        "ru.smartbot.infrastructure..",
                        "ru.smartbot.app..",
                        "com.fasterxml.jackson..",
                        "reactor..");
        rule.check(classes);
    }

    @Test
    @DisplayName("Common не зависит ни от чего, кроме JDK")
    void commonIsPure() {
        ArchRule rule = noClasses().that().resideInAPackage("ru.smartbot.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "ru.smartbot.domain..",
                        "ru.smartbot.application..",
                        "ru.smartbot.adapter..",
                        "ru.smartbot.infrastructure..",
                        "ru.smartbot.app..");
        rule.check(classes);
    }

    @Test
    @DisplayName("Application API (порты) не зависит от Spring и реализаций")
    void applicationApiIsFrameworkFree() {
        ArchRule rule = noClasses().that().resideInAPackage("ru.smartbot.application.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "ru.smartbot.adapter..",
                        "ru.smartbot.application.impl..",
                        "ru.smartbot.infrastructure..",
                        "ru.smartbot.app..");
        rule.check(classes);
    }

    @Test
    @DisplayName("Application Impl не зависит от адаптеров и Spring Web")
    void applicationImplDoesNotSeeAdapters() {
        ArchRule rule = noClasses().that().resideInAPackage("ru.smartbot.application.impl..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "ru.smartbot.adapter..",
                        "ru.smartbot.infrastructure..",
                        "ru.smartbot.app..",
                        "org.springframework.web..",
                        "org.springframework.boot..");
        rule.check(classes);
    }

    @Test
    @DisplayName("Адаптеры не зависят друг от друга, от application-impl и app")
    void adaptersAreIsolated() {
        String[] adapters = {"telegram", "llm", "search", "persistence"};
        for (String adapter : adapters) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("ru.smartbot.adapter." + adapter + "..")
                    .should().dependOnClassesThat(
                            DescribedPredicate.describe(
                                    "другой адаптер, application-impl, infrastructure или app",
                                    (JavaClass javaClass) -> {
                                        String name = javaClass.getPackageName();
                                        if (!name.startsWith("ru.smartbot.")
                                                || name.startsWith("ru.smartbot.adapter." + adapter)) {
                                            return false; // свой пакет адаптера разрешён
                                        }
                                        return name.startsWith("ru.smartbot.adapter.")
                                                || name.startsWith("ru.smartbot.application.impl")
                                                || name.startsWith("ru.smartbot.infrastructure")
                                                || name.startsWith("ru.smartbot.app");
                                    }));
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("Никто, кроме app, не зависит от composition root")
    void onlyAppSeesApp() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("ru.smartbot.app..")
                .should().dependOnClassesThat().resideInAPackage("ru.smartbot.app..");
        rule.check(classes);
    }
}

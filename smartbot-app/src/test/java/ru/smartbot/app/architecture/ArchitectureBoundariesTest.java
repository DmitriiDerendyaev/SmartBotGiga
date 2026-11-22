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

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
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
                                        String ownAdapter = "ru.smartbot.adapter." + adapter;
                                        if (!name.startsWith("ru.smartbot.")
                                                || name.equals(ownAdapter)
                                                || name.startsWith(ownAdapter + ".")) {
                                            return false; // свой пакет адаптера разрешён
                                        }
                                        return name.startsWith("ru.smartbot.adapter.")
                                                || inPackage(name, "ru.smartbot.application.impl")
                                                || inPackage(name, "ru.smartbot.infrastructure")
                                                || inPackage(name, "ru.smartbot.app");
                                    }));
            rule.check(classes);
        }
    }

    /**
     * Точная проверка пакета: сам пакет или его подпакеты (без ложных префиксов
     * вида "ru.smartbot.app" → "ru.smartbot.application").
     */
    private static boolean inPackage(String packageName, String prefix) {
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }

    /**
     * Страж user-facing пути (AD-20): адаптер не рендерит сырые детали исключения —
     * ни getMessage(), ни getLocalizedMessage(), ни toString(), ни printStackTrace()
     * на Throwable-assignable owner'ах. Наружу — только ErrorCode + SafeErrorMessages.
     *
     * <p>Известные ограничения: {@code String.valueOf(e)} и конкатенация строк
     * ({@code "..." + e}) идут через invokedynamic (StringConcatFactory) и данным
     * правилом не ловятся; проверка port-shape (адаптер возвращает наружу только
     * доменные типы) отложена до появления user-facing контрактов.
     */
    @Test
    @DisplayName("Адаптеры не вызывают getMessage()/getLocalizedMessage()/toString()/printStackTrace() на Throwable: user-facing путь только через ErrorCode (AD-20)")
    void adaptersNeverCallGetMessage() {
        ArchRule rule = noClasses().that().resideInAPackage("ru.smartbot.adapter..")
                .should().callMethodWhere(
                        target(owner(assignableTo(Throwable.class)))
                                .and(target(nameMatching(
                                        "getMessage|getLocalizedMessage|toString|printStackTrace"))));
        rule.check(classes);
    }

    @Test
    @DisplayName("Никто, кроме app, не зависит от composition root")
    void onlyAppSeesApp() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("ru.smartbot.app..")
                .should().dependOnClassesThat().resideInAPackage("ru.smartbot.app..");
        rule.check(classes);
    }
}

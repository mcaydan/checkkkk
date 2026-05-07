package com.puppycrawl.tools.checkstyle;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    public static void setup() {
        // Tüm projeyi tarıyoruz
        importedClasses = new ClassFileImporter()
                .importPackages("com.puppycrawl.tools.checkstyle");
    }

    // Rule 1: Core should not depend on Checks (Test sınıflarını basitçe dışarıda bırakıyoruz)
    @Test
    void core_should_be_independent() {
        noClasses()
                .that().resideInAPackage("com.puppycrawl.tools.checkstyle")
                .and().haveSimpleNameNotEndingWith("Test") // "Test" ile bitmeyenleri al (En garantisi budur)
                .should().dependOnClassesThat().resideInAPackage("..checks..")
                .because("The Microkernel core must be independent of specific plugin implementations.")
                .check(importedClasses);
    }

    // Rule 2: TreeWalker dependency validation (Utils ve JaCoCo izinli)
    @Test
    void treeWalker_realization_validation() {
        classes().that().haveSimpleName("TreeWalker")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..api..",
                        "..utils..",
                        "java..",
                        "org.antlr.v4.runtime..",
                        "com.puppycrawl.tools.checkstyle",
                        "org.jacoco.agent.rt.."
                )
                .check(importedClasses);
    }

    // Rule 3: Parser Independence
    @Test
    void parser_independence_validation() {
        noClasses()
                .that().resideInAPackage("..grammar..")
                .and().haveSimpleNameNotEndingWith("Test")
                .should().dependOnClassesThat().resideInAPackage("..checks..")
                .check(importedClasses);
    }

    // Rule 4: Cyclic Dependency Check
    @Test
    void no_cycles() {
        slices().matching("com.puppycrawl.tools.checkstyle.(*)..")
                .should().beFreeOfCycles()
                .check(importedClasses);
    }
}
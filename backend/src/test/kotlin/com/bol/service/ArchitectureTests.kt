package com.bol.service

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * Validates the architectural constraints defined in the architecture.adl specification.
 */
@AnalyzeClasses(
    packages = ["com.bol.service"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class ArchitectureTests {

    @ArchTest
    val `no circular dependencies allowed`: ArchRule =
        slices()
            .matching("com.bol.service.(*)..")
            .should().beFreeOfCycles()
            .because("Circular dependencies make the system hard to maintain and refactor.")

    @ArchTest
    val `verify layered architecture`: ArchRule =
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controllers").definedBy("..controller..")
            .layer("Services").definedBy("..service..")
            .layer("Configuration").definedBy("..config..")
            
            // Controllers should only use Services or config
            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            
            // Services should only be accessed by Controllers or config classes
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Configuration")
            
            .because("We must ensure a clean separation between the HTTP delivery layer and business logic (RAG, Document Services).")

    @ArchTest
    val `controllers must reside in the controller package`: ArchRule =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..")
            .because("All Chat API and Document API endpoints must be organized in the correct Backend Services subdomain.")

    @ArchTest
    val `services must reside in the service package`: ArchRule =
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..")
            .because("All RAG Service and Document Services must be contained within the Services subdomain.")

    @ArchTest
    val `controllers must be annotated with RestController`: ArchRule =
        classes()
            .that().resideInAPackage("..controller..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController::class.java)
            .because("Classes in the controller package handling HTTP requests should be valid Spring RestControllers.")

    @ArchTest
    val `services must be annotated with Service`: ArchRule =
        classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Service")
            .should().beAnnotatedWith(org.springframework.stereotype.Service::class.java)
            .because("Business logic components must be discoverable as Spring Services.")
}

package com.katariastoneworld.apis.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.katariastoneworld.apis",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule services_must_not_depend_on_controller_http_layer =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "org.springframework.web..",
                            "jakarta.servlet..");

    @ArchTest
    static final ArchRule repositories_must_remain_data_layer_only =
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..service..",
                            "..dto..");
}

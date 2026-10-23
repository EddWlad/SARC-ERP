package com.tidsec.sarc_bakend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.tidsec.sarc_bakend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domain_is_free_of_spring_and_outer_layers = noClasses()
			.that().resideInAPackage("..modules..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..application..",
					"..infrastructure..", "..api..");

	@ArchTest
	static final ArchRule application_does_not_know_adapters = noClasses()
			.that().resideInAPackage("..modules..application..")
			.should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..api..");

	@ArchTest
	static final ArchRule api_does_not_reach_infrastructure = noClasses()
			.that().resideInAPackage("..modules..api..")
			.should().dependOnClassesThat().resideInAPackage("..infrastructure..");

	@ArchTest
	static final ArchRule controllers_live_in_api_controller = classes()
			.that().areAnnotatedWith(RestController.class)
			.should().resideInAPackage("..modules..api.controller..");

	/**
	 * Vocabulario y contratos compartidos que usan los dominios de los modulos: deben ser Java puro,
	 * igual que esos dominios.
	 */
	@ArchTest
	static final ArchRule shared_kernel_is_free_of_spring = noClasses()
			.that().resideInAnyPackage("..shared.domain..", "..shared.catalog..", "..shared.pagination..")
			.should().dependOnClassesThat().resideInAPackage("org.springframework..");

	@ArchTest
	static final ArchRule shared_does_not_depend_on_modules = noClasses()
			.that().resideInAPackage("..shared..")
			.should().dependOnClassesThat().resideInAPackage("..modules..");

	@ArchTest
	static final ArchRule modules_do_not_touch_each_other_directly = slices()
			.matching("..modules.(*)..")
			.should().notDependOnEachOther();
}

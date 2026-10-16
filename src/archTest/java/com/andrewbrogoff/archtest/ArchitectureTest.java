package com.andrewbrogoff.archtest;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The rules of architecture, "Enforcement", over the shipped classes (the mod jar). */
@AnalyzeClasses(packages = "com.andrewbrogoff.vftv")
class ArchitectureTest {
    private static final VftvRules RULES = new VftvRules("com.andrewbrogoff.vftv");

    @ArchTest static final ArchRule NO_REFLECTION = RULES.noReflection();
    @ArchTest static final ArchRule LAYERS = RULES.layers();
    @ArchTest static final ArchRule CORE_IS_LOADER_FREE = RULES.coreIsLoaderFree();
    @ArchTest static final ArchRule API_IS_PURE = RULES.apiIsPure();
    @ArchTest static final ArchRule MODEL_IS_MINECRAFT_FREE = RULES.modelIsMinecraftFree();
    @ArchTest static final ArchRule CLIENT_SEPARATION = RULES.clientSeparation();
    @ArchTest static final ArchRule CONFIG_SIDES = RULES.configSides();
    @ArchTest static final ArchRule FEATURES_ACYCLIC = RULES.featuresAcyclic();
    @ArchTest static final ArchRule MIXINS_CONFINED = RULES.mixinsConfined();
    @ArchTest static final ArchRule COMPAT_CONFINED = RULES.compatConfined();
    @ArchTest static final ArchRule FLUX_LEAF = RULES.fluxLeaf();
    @ArchTest static final ArchRule COMMON_LEAF = RULES.commonLeaf();
    @ArchTest static final ArchRule FLUX_ENTRY_POINTS = RULES.fluxEntryPoints();
    @ArchTest static final ArchRule API_PACKAGES = RULES.apiPackages();
    @ArchTest static final ArchRule API_SHAPES = RULES.apiShapes();
    @ArchTest static final ArchRule API_TIER_LEAK = RULES.apiTierLeak();
    @ArchTest static final ArchRule API_CONSTRUCTION = RULES.apiConstruction();
}

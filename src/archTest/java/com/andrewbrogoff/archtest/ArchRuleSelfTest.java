package com.andrewbrogoff.archtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves each rule bites: applied to the fixture tree {@code fixture.vftv}, each reports the
 * fixture built to break it, and stays quiet about the permitted ones.
 */
class ArchRuleSelfTest {
    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("fixture.vftv");
    private static final VftvRules RULES = new VftvRules("fixture.vftv");

    private static String report(ArchRule rule) {
        EvaluationResult result = rule.evaluate(FIXTURES);
        assertTrue(result.hasViolation(), () -> rule.getDescription() + " found nothing");
        return String.join("\n", result.getFailureReport().getDetails());
    }

    private static void bites(ArchRule rule, List<String> reported, List<String> quiet) {
        String report = report(rule);
        for (String expected : reported) {
            assertTrue(report.contains(expected), () -> "missing " + expected + " in:\n" + report);
        }
        for (String allowed : quiet) {
            assertFalse(
                    report.contains(allowed),
                    () -> "wrongly reported " + allowed + " in:\n" + report);
        }
    }

    @Test
    void noReflection() {
        bites(RULES.noReflection(), List.of("Class.forName", "ServiceLoader"), List.of());
    }

    @Test
    void layers() {
        bites(RULES.layers(), List.of("Platformy", "GoodEvent"), List.of());
    }

    @Test
    void coreIsLoaderFree() {
        bites(
                RULES.coreIsLoaderFree(),
                List.of("Alpha", "net.neoforged.bus.api.Event"),
                List.of("FixtureMod", "GoodEvent"));
    }

    @Test
    void apiIsPure() {
        bites(RULES.apiIsPure(), List.of("Impure"), List.of("GoodEvent"));
    }

    @Test
    void modelIsMinecraftFree() {
        bites(RULES.modelIsMinecraftFree(), List.of("AlphaModel"), List.of());
    }

    @Test
    void clientSeparation() {
        bites(
                RULES.clientSeparation(),
                List.of("net.minecraft.client.Minecraft"),
                List.of("ClientSide"));
    }

    @Test
    void configSides() {
        bites(
                RULES.configSides(),
                List.of("MisplacedClientConfig", "Alpha.clientConfig", "ClientSide.serverConfig"),
                List.of("Adapter"));
    }

    @Test
    void featuresAcyclic() {
        bites(RULES.featuresAcyclic(), List.of("Beta"), List.of());
    }

    @Test
    void mixinsConfined() {
        bites(RULES.mixinsConfined(), List.of("Alpha", "NoReason"), List.of("WithReason"));
    }

    @Test
    void compatConfined() {
        bites(
                RULES.compatConfined(),
                List.of("IModPlugin", "Sneaky.entryFromElsewhere", "Sneaky.inside"),
                List.of("FixtureMod", "CuriosApi"));
    }

    @Test
    void fluxLeaf() {
        bites(RULES.fluxLeaf(), List.of("FluxService.leak"), List.of("FluxService.shared"));
    }

    @Test
    void commonLeaf() {
        bites(RULES.commonLeaf(), List.of("Common.leak"), List.of());
    }

    @Test
    void fluxEntryPoints() {
        bites(RULES.fluxEntryPoints(), List.of("Alpha.deposit"), List.of("HushSpread"));
    }

    @Test
    void apiPackages() {
        bites(RULES.apiPackages(), List.of("Orphan"), List.of("GoodEvent", "AlphaKeys"));
    }

    @Test
    void apiShapes() {
        bites(
                RULES.apiShapes(),
                List.of("Logic", "OpenEvent", "ModBusEvent", "CancelledEvent"),
                List.of("GoodEvent", "AlphaKeys", "Owned", "Leaky"));
    }

    @Test
    void apiTierLeak() {
        bites(
                RULES.apiTierLeak(),
                List.of(
                        "Leaky.secrets() is Stable but mentions INTERNAL",
                        "mentions EXPERIMENTAL fixture.vftv.api.alpha.Draft"),
                List.of("GoodEvent"));
    }

    @Test
    void apiConstruction() {
        bites(RULES.apiConstruction(), List.of("Unowned"), List.of("alpha.Owned", "GoodEvent"));
    }

    @Test
    void effectiveTiers() {
        assertEquals(
                VftvRules.Tier.STABLE,
                VftvRules.tier(FIXTURES.get("fixture.vftv.api.alpha.AlphaKeys")));
        assertEquals(
                VftvRules.Tier.STABLE,
                VftvRules.tier(FIXTURES.get("fixture.vftv.api.alpha.AlphaKeys$Nested")));
        assertEquals(
                VftvRules.Tier.EXPERIMENTAL,
                VftvRules.tier(FIXTURES.get("fixture.vftv.api.alpha.Draft")));
        assertEquals(
                VftvRules.Tier.INTERNAL,
                VftvRules.tier(FIXTURES.get("fixture.vftv.api.alpha.Secret")));
        assertEquals(
                VftvRules.Tier.EXPERIMENTAL,
                VftvRules.tier(FIXTURES.get("fixture.vftv.api.nosuchfeature.Orphan")),
                "no annotation anywhere means Experimental");
    }
}

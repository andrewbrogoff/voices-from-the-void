package com.andrewbrogoff.archtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The effectively Stable API types equal the API spec's Stable allowlist, so nothing becomes Stable
 * by accident (API spec, "Tests"). A system ticket that adds an allowlisted type amends nothing
 * here; one that promotes a type amends the spec and this list together.
 */
class StableAllowlistTest {
    /**
     * The API spec's "The Stable allowlist", by simple name; plus every *Keys and *Limits holder.
     */
    static final Set<String> ALLOWLIST =
            Set.of(
                    "Stable",
                    "Experimental",
                    "Internal",
                    "RoadLayout",
                    "Aspect",
                    "HiddenPrimal",
                    "WandRod",
                    "WandCap",
                    "NodeType",
                    "Research",
                    "ResearchCategory",
                    "FocusEffect",
                    "FocusMedium",
                    "FocusModifier",
                    "GolemMaterial",
                    "VoiceEvent",
                    "Bargain",
                    "ChoirSite",
                    "Chorister",
                    "HushedForm",
                    "AspectDataMaps",
                    "WarpDataMaps",
                    "ArmorDataMaps",
                    "AspectValues",
                    "Wearable",
                    "WearableSlot",
                    "EssentiaResource",
                    "EssentiaCapabilities",
                    "EssentiaSuction",
                    "GolemJob",
                    "GolemCarryKind",
                    "GolemRole",
                    "GolemCore",
                    "ResearchView",
                    "ResearchCapabilities",
                    "BaffleView",
                    "ArmorCapabilities");

    static boolean allowlisted(JavaClass c) {
        String name = c.getSimpleName();
        boolean holder =
                c.getEnclosingClass().isEmpty()
                        && (name.endsWith("Keys") || name.endsWith("Limits"));
        return holder || ALLOWLIST.contains(name);
    }

    @Test
    void effectivelyStableTypesAreExactlyTheAllowlisted() {
        VftvRules rules = new VftvRules("com.andrewbrogoff.vftv");
        JavaClasses api = new ClassFileImporter().importPackages("com.andrewbrogoff.vftv.api");
        Set<String> stableNotListed = new TreeSet<>();
        Set<String> listedNotStable = new TreeSet<>();
        for (JavaClass c : api) {
            if (!rules.isApi(c) || VftvRules.isSynthetic(c)) {
                continue;
            }
            boolean stable = VftvRules.tier(c) == VftvRules.Tier.STABLE;
            boolean listed =
                    allowlisted(c)
                            || c.getEnclosingClass()
                                    .map(StableAllowlistTest::allowlisted)
                                    .orElse(false);
            if (stable && !listed) {
                stableNotListed.add(c.getName());
            }
            if (listed && !stable) {
                listedNotStable.add(c.getName());
            }
        }
        assertTrue(
                api.contain("com.andrewbrogoff.vftv.api.Stable"), "the api classes were imported");
        assertEquals(Set.of(), stableNotListed, "Stable but not on the allowlist");
        assertEquals(Set.of(), listedNotStable, "on the allowlist but not Stable");
    }
}

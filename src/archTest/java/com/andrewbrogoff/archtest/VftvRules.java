package com.andrewbrogoff.archtest;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every rule in architecture, "Enforcement" that can apply before the 1.0.0 release, over a root
 * package. Production applies them to {@code com.andrewbrogoff.vftv}; the self-test applies them to
 * the fixture tree {@code fixture.vftv}, proving each one bites. Rules about features that do not
 * exist yet allow an empty match, so they hold vacuously until the package appears.
 */
final class VftvRules {
    /** The tier annotations are the API's own, whatever the root. */
    static final String STABLE = "com.andrewbrogoff.vftv.api.Stable";

    static final String EXPERIMENTAL = "com.andrewbrogoff.vftv.api.Experimental";
    static final String INTERNAL = "com.andrewbrogoff.vftv.api.Internal";

    /** The only cancellable events (API spec, "The events"). */
    static final Set<String> CANCELLABLE =
            Set.of("FocusAffectEntityEvent", "GolemWorkEvent", "HushSpreadEvent");

    /** The addon-constructible types (API spec, "Construction ownership"). */
    static final Set<String> ADDON_CONSTRUCTIBLE =
            Set.of("EssentiaResource", "AspectValues", "Wearable", "GolemJob");

    private static final String EVENT = "net.neoforged.bus.api.Event";
    private static final String CANCELLABLE_EVENT = "net.neoforged.bus.api.ICancellableEvent";
    private static final String MOD_BUS_EVENT = "net.neoforged.fml.event.IModBusEvent";
    private static final String MOD_ANNOTATION = "net.neoforged.fml.common.Mod";
    private static final String REFLECTIVE_CLASS_METHODS =
            "forName|newInstance|get(Declared)?(Method|Field|Constructor|RecordComponent)s?"
                    + "|getEnclosingMethod|getEnclosingConstructor|getPermittedSubclasses|setAccessible";

    enum Tier {
        STABLE,
        EXPERIMENTAL,
        INTERNAL
    }

    private final String root;

    VftvRules(String root) {
        this.root = root;
    }

    private String in(String suffix) {
        return root + suffix;
    }

    ArchRule noReflection() {
        return noClasses()
                .that()
                .resideInAPackage(in(".."))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "java.lang.reflect..", "java.lang.invoke..", "sun.misc..", "jdk.internal..")
                .orShould()
                .accessTargetWhere(
                        target(owner(equivalentTo(Class.class)))
                                .and(target(nameMatching(REFLECTIVE_CLASS_METHODS))))
                .orShould()
                .callMethodWhere(target(owner(resideInAnyPackage("java.lang.invoke.."))))
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("net.neoforged.fml.util.ObfuscationReflectionHelper")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.util.ServiceLoader")
                .because("runtime reflection and service discovery are banned (ADR 0002)");
    }

    ArchRule layers() {
        ArchRule layered =
                layeredArchitecture()
                        .consideringOnlyDependenciesInAnyPackage(in(".."))
                        .withOptionalLayers(true)
                        .layer("Api")
                        .definedBy(in(".api.."))
                        .layer("Core")
                        .definedBy(in(".core.."))
                        .layer("Datagen")
                        .definedBy(in(".datagen.."))
                        .layer("Platform")
                        .definedBy(in(".neoforge.."))
                        .whereLayer("Platform")
                        .mayNotBeAccessedByAnyLayer()
                        .whereLayer("Datagen")
                        .mayOnlyBeAccessedByLayers("Platform")
                        .whereLayer("Core")
                        .mayOnlyBeAccessedByLayers("Datagen", "Platform")
                        .whereLayer("Api")
                        .mayOnlyBeAccessedByLayers("Core", "Datagen", "Platform");
        ArchRule coreNotApiNeoforge =
                noClasses()
                        .that()
                        .resideInAPackage(in(".core.."))
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage(in(".api.neoforge.."))
                        .allowEmptyShould(true);
        return CompositeArchRule.of(layered).and(coreNotApiNeoforge);
    }

    ArchRule coreIsLoaderFree() {
        return noClasses()
                .that(
                        resideInAPackage(in(".core.."))
                                .or(
                                        resideInAPackage(in(".api.."))
                                                .and(
                                                        resideOutsideOfPackage(
                                                                in(".api.neoforge..")))))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.neoforged..", "org.spongepowered..");
    }

    ArchRule apiIsPure() {
        return classes()
                .that()
                .resideInAPackage(in(".api.."))
                .and()
                .resideOutsideOfPackage(in(".api.neoforge.."))
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "net.minecraft..",
                        "com.mojang..",
                        "org.jspecify..",
                        in(".api.."));
    }

    ArchRule modelIsMinecraftFree() {
        return noClasses()
                .that()
                .resideInAPackage(in("..model.."))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.minecraft..", "com.mojang..")
                .allowEmptyShould(true);
    }

    ArchRule clientSeparation() {
        return noClasses()
                .that()
                .resideInAPackage(in(".."))
                .and()
                .resideOutsideOfPackage("..client..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..");
    }

    ArchRule configSides() {
        DescribedPredicate<JavaClass> clientConfig =
                simpleNameEndingWith("ClientConfig")
                        .and(resideInAPackage(in("..")))
                        .or(resideInAPackage(in(".neoforge.config.client..")));
        DescribedPredicate<JavaClass> serverConfig =
                simpleNameEndingWith("Config")
                        .and(DescribedPredicate.not(simpleNameEndingWith("ClientConfig")))
                        .and(resideInAPackage(in("..")))
                        .or(
                                resideInAPackage(in(".neoforge.config.."))
                                        .and(
                                                resideOutsideOfPackage(
                                                        in(".neoforge.config.client.."))));
        ArchRule declared =
                classes()
                        .that()
                        .haveSimpleNameEndingWith("ClientConfig")
                        .and()
                        .resideInAPackage(in(".."))
                        .should()
                        .resideInAPackage("..client..")
                        .allowEmptyShould(true);
        ArchRule usedFromClient =
                noClasses()
                        .that()
                        .resideInAPackage(in(".."))
                        .and()
                        .resideOutsideOfPackage("..client..")
                        .should()
                        .dependOnClassesThat(clientConfig);
        ArchRule clientUsesNoServerConfig =
                noClasses()
                        .that()
                        .resideInAPackage(in("..client.."))
                        .should()
                        .dependOnClassesThat(serverConfig)
                        .allowEmptyShould(true);
        return CompositeArchRule.of(declared).and(usedFromClient).and(clientUsesNoServerConfig);
    }

    ArchRule featuresAcyclic() {
        return slices().matching(in(".core.(*).."))
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true);
    }

    ArchRule mixinsConfined() {
        ArchRule confined =
                noClasses()
                        .that()
                        .resideInAPackage(in(".."))
                        .and()
                        .resideOutsideOfPackage(in(".neoforge.mixin.."))
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("org.spongepowered..");
        ArchRule explained =
                classes()
                        .that()
                        .resideInAPackage(in(".neoforge.mixin.."))
                        .and()
                        .doNotHaveSimpleName("WhyMixin")
                        .should()
                        .beAnnotatedWith(in(".neoforge.mixin.WhyMixin"))
                        .allowEmptyShould(true);
        return CompositeArchRule.of(confined).and(explained);
    }

    ArchRule compatConfined() {
        String compat = in(".neoforge.compat.");
        ArchRule jei =
                noClasses()
                        .that()
                        .resideInAPackage(in(".."))
                        .and()
                        .resideOutsideOfPackage(compat + "jei..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("mezz.jei..");
        ArchRule curios =
                noClasses()
                        .that()
                        .resideInAPackage(in(".."))
                        .and()
                        .resideOutsideOfPackage(compat + "curios..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("top.theillusivec4.curios..");
        ArchCondition<JavaClass> onlyThroughEntry =
                new ArchCondition<>("reach a compat package only through its entry class") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String own = compatModule(item, compat);
                        for (Dependency d : item.getDirectDependenciesFromSelf()) {
                            JavaClass target = d.getTargetClass();
                            String module = compatModule(target, compat);
                            if (module == null || module.equals(own)) {
                                continue;
                            }
                            boolean entry =
                                    module.equals("curios")
                                            && target.getSimpleName().equals("CuriosCompat")
                                            && item.isAnnotatedWith(MOD_ANNOTATION);
                            if (!entry) {
                                events.add(SimpleConditionEvent.violated(item, d.getDescription()));
                            }
                        }
                    }
                };
        ArchRule entries = classes().that().resideInAPackage(in("..")).should(onlyThroughEntry);
        return CompositeArchRule.of(jei).and(curios).and(entries);
    }

    private static String compatModule(JavaClass c, String compatPrefix) {
        String pkg = c.getPackageName() + ".";
        if (!pkg.startsWith(compatPrefix)) {
            return null;
        }
        String rest = pkg.substring(compatPrefix.length());
        return rest.isEmpty() ? "" : rest.substring(0, rest.indexOf('.'));
    }

    ArchRule fluxLeaf() {
        return noClasses()
                .that()
                .resideInAPackage(in(".core.flux.."))
                .should()
                .dependOnClassesThat(otherFeature("flux", Set.of("common")))
                .allowEmptyShould(true)
                .because(
                        "core.flux imports only api, the JDK and the shared leaf core.common"
                                + " (architecture, Layers)");
    }

    ArchRule commonLeaf() {
        return noClasses()
                .that()
                .resideInAPackage(in(".core.common.."))
                .should()
                .dependOnClassesThat(
                        resideInAPackage(in(".core.."))
                                .and(resideOutsideOfPackage(in(".core.common.."))))
                .allowEmptyShould(true);
    }

    /** A class in a core feature other than {@code feature} and the {@code allowed} ones. */
    private DescribedPredicate<JavaClass> otherFeature(String feature, Set<String> allowed) {
        String core = in(".core.");
        return DescribedPredicate.describe(
                "in another core feature",
                c -> {
                    String pkg = c.getPackageName() + ".";
                    if (!pkg.startsWith(core)) {
                        return false;
                    }
                    String rest = pkg.substring(core.length());
                    if (rest.isEmpty()) {
                        return true; // the core root (Vftv, Platform) depends on every feature
                    }
                    String slice = rest.substring(0, rest.indexOf('.'));
                    return !slice.equals(feature) && !allowed.contains(slice);
                });
    }

    ArchRule fluxEntryPoints() {
        return noClasses()
                .that()
                .resideOutsideOfPackages(in(".neoforge.hush.."), in(".core.hush.."))
                .and()
                .doNotHaveSimpleName("FluxService")
                .should()
                .callMethodWhere(
                        target(name("deposit")).and(target(owner(simpleName("FluxService")))))
                .allowEmptyShould(true);
    }

    ArchRule apiPackages() {
        ArchCondition<JavaClass> condition =
                new ArchCondition<>("live in the api root or an api package for a core feature") {
                    private final Set<String> allowed = new TreeSet<>();

                    @Override
                    public void init(Collection<JavaClass> all) {
                        allowed.add(in(".api"));
                        String core = in(".core.");
                        for (JavaClass c : all) {
                            String pkg = c.getPackageName() + ".";
                            if (pkg.startsWith(core) && pkg.length() > core.length()) {
                                String slice =
                                        pkg.substring(
                                                core.length(), pkg.indexOf('.', core.length()));
                                allowed.add(in(".api." + slice));
                                allowed.add(in(".api.neoforge." + slice));
                            }
                        }
                    }

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (isApi(item) && !allowed.contains(item.getPackageName())) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            item,
                                            item.getName()
                                                    + " is in "
                                                    + item.getPackageName()
                                                    + ", which mirrors no core feature"));
                        }
                    }
                };
        return classes().should(condition);
    }

    ArchRule apiShapes() {
        ArchCondition<JavaClass> condition =
                new ArchCondition<>("have an allowed API shape") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        if (!isApi(c) || isSynthetic(c)) {
                            return;
                        }
                        String problem = shapeProblem(c);
                        if (problem != null) {
                            events.add(
                                    SimpleConditionEvent.violated(c, c.getName() + " " + problem));
                        }
                    }
                };
        return classes().should(condition);
    }

    private String shapeProblem(JavaClass c) {
        if (c.isInterface() || c.isRecord() || c.isEnum() || c.isAnnotation()) {
            return null;
        }
        if (c.isAssignableTo(EVENT)) {
            if (!c.getPackageName().startsWith(in(".api.neoforge."))) {
                return "is an event outside api.neoforge";
            }
            if (!c.getModifiers().contains(JavaModifier.FINAL)) {
                return "is an event that is not final";
            }
            if (c.isAssignableTo(MOD_BUS_EVENT)) {
                return "is a mod-bus event";
            }
            boolean cancellable = c.isAssignableTo(CANCELLABLE_EVENT);
            if (cancellable != CANCELLABLE.contains(c.getSimpleName())) {
                return cancellable
                        ? "is cancellable but not listed"
                        : "is listed cancellable but is not";
            }
            return null;
        }
        boolean constantHolder =
                c.getModifiers().contains(JavaModifier.FINAL)
                        && c.getRawSuperclass()
                                .map(s -> s.isEquivalentTo(Object.class))
                                .orElse(true)
                        && c.getConstructors().stream()
                                .allMatch(k -> k.getModifiers().contains(JavaModifier.PRIVATE))
                        && c.getFields().stream()
                                .allMatch(
                                        f ->
                                                f.getModifiers()
                                                        .containsAll(
                                                                Set.of(
                                                                        JavaModifier.PUBLIC,
                                                                        JavaModifier.STATIC,
                                                                        JavaModifier.FINAL)));
        return constantHolder
                ? null
                : "is not an interface, record, enum, annotation, constant holder or event";
    }

    ArchRule apiTierLeak() {
        ArchCondition<JavaClass> condition =
                new ArchCondition<>("mention only Stable API types in Stable signatures") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        if (!isApi(c) || isSynthetic(c)) {
                            return;
                        }
                        if (tier(c) == Tier.STABLE) {
                            Set<JavaClass> types = new HashSet<>();
                            c.getSuperclass()
                                    .ifPresent(t -> types.addAll(t.getAllInvolvedRawTypes()));
                            c.getInterfaces()
                                    .forEach(t -> types.addAll(t.getAllInvolvedRawTypes()));
                            addBounds(c.getTypeParameters(), types);
                            report(c.getName(), types, events, c);
                        }
                        for (JavaMember m : members(c)) {
                            if (exposed(m) && tier(m) == Tier.STABLE) {
                                report(m.getFullName(), signature(m), events, c);
                            }
                        }
                    }
                };
        return classes().should(condition);
    }

    private void report(String where, Set<JavaClass> types, ConditionEvents events, JavaClass c) {
        for (JavaClass t : types) {
            if (isApi(t) && tier(t) != Tier.STABLE) {
                events.add(
                        SimpleConditionEvent.violated(
                                c,
                                where + " is Stable but mentions " + tier(t) + " " + t.getName()));
            }
        }
    }

    private static List<JavaMember> members(JavaClass c) {
        List<JavaMember> out = new java.util.ArrayList<>();
        out.addAll(c.getFields());
        out.addAll(c.getMethods());
        out.addAll(c.getConstructors());
        return out;
    }

    private static boolean exposed(JavaMember m) {
        Set<JavaModifier> mods = m.getModifiers();
        return (mods.contains(JavaModifier.PUBLIC) || mods.contains(JavaModifier.PROTECTED))
                && !mods.contains(JavaModifier.SYNTHETIC)
                && !mods.contains(JavaModifier.BRIDGE);
    }

    private static Set<JavaClass> signature(JavaMember m) {
        Set<JavaClass> types = new HashSet<>();
        if (m instanceof JavaField f) {
            types.addAll(f.getType().getAllInvolvedRawTypes());
        } else if (m instanceof JavaCodeUnit u) {
            types.addAll(u.getReturnType().getAllInvolvedRawTypes());
            u.getParameterTypes().forEach(t -> types.addAll(t.getAllInvolvedRawTypes()));
            types.addAll(u.getThrowsClause().getTypes());
            addBounds(u.getTypeParameters(), types);
        }
        return types;
    }

    private static void addBounds(
            List<? extends JavaTypeVariable<?>> variables, Set<JavaClass> types) {
        for (JavaTypeVariable<?> v : variables) {
            for (JavaType bound : v.getBounds()) {
                types.addAll(bound.getAllInvolvedRawTypes());
            }
        }
    }

    ArchRule apiConstruction() {
        ArchCondition<JavaClass> condition =
                new ArchCondition<>("follow the API's construction ownership") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        if (!isApi(c)
                                || isSynthetic(c)
                                || !(c.isRecord() || c.isAssignableTo(EVENT))) {
                            return;
                        }
                        boolean addon = ADDON_CONSTRUCTIBLE.contains(c.getSimpleName());
                        boolean anyInternal = false;
                        for (JavaConstructor k : c.getConstructors()) {
                            boolean isPublic = k.getModifiers().contains(JavaModifier.PUBLIC);
                            boolean internal = k.isAnnotatedWith(INTERNAL);
                            anyInternal |= internal;
                            if (isPublic && addon && tier(k) != Tier.STABLE) {
                                events.add(
                                        SimpleConditionEvent.violated(
                                                c, k.getFullName() + " must be public and Stable"));
                            }
                            if (isPublic && !addon && !internal) {
                                events.add(
                                        SimpleConditionEvent.violated(
                                                c,
                                                k.getFullName() + " is public but not @Internal"));
                            }
                        }
                        if (!addon && !anyInternal) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            c, c.getName() + " declares no @Internal constructor"));
                        }
                    }
                };
        return classes().should(condition);
    }

    boolean isApi(JavaClass c) {
        String pkg = c.getPackageName();
        return pkg.equals(in(".api")) || pkg.startsWith(in(".api."));
    }

    static boolean isSynthetic(JavaClass c) {
        return c.getModifiers().contains(JavaModifier.SYNTHETIC) || c.isAnonymousClass();
    }

    /**
     * The effective tier: the declaration's own annotation, else its nearest enclosing type's, else
     * its package's, else Experimental (API spec, "The three tiers").
     */
    static Tier tier(JavaClass c) {
        return own(c).or(() -> c.getEnclosingClass().map(VftvRules::tier))
                .or(() -> own(c.getPackage()))
                .orElse(Tier.EXPERIMENTAL);
    }

    static Tier tier(JavaMember m) {
        return own(m).orElseGet(() -> tier(m.getOwner()));
    }

    private static Optional<Tier> own(CanBeAnnotated annotated) {
        if (annotated.isAnnotatedWith(INTERNAL)) {
            return Optional.of(Tier.INTERNAL);
        }
        if (annotated.isAnnotatedWith(EXPERIMENTAL)) {
            return Optional.of(Tier.EXPERIMENTAL);
        }
        if (annotated.isAnnotatedWith(STABLE)) {
            return Optional.of(Tier.STABLE);
        }
        return Optional.empty();
    }

    private static Optional<Tier> own(com.tngtech.archunit.core.domain.JavaPackage pkg) {
        if (pkg.isAnnotatedWith(INTERNAL)) {
            return Optional.of(Tier.INTERNAL);
        }
        if (pkg.isAnnotatedWith(EXPERIMENTAL)) {
            return Optional.of(Tier.EXPERIMENTAL);
        }
        if (pkg.isAnnotatedWith(STABLE)) {
            return Optional.of(Tier.STABLE);
        }
        return Optional.empty();
    }
}

package com.andrewbrogoff.vftv.datagen.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The client's supported language codes, read from the language metadata of the client resource
 * packs datagen sees, which include the pinned vanilla assets.
 */
public final class LanguageCatalogue {
    private LanguageCatalogue() {}

    /** Every language code the packs declare; fails if they declare none. */
    public static Set<String> of(ResourceManager resources) {
        Set<String> codes = new TreeSet<>();
        for (PackResources pack : resources.listPacks().toList()) {
            try {
                LanguageMetadataSection section =
                        pack.getMetadataSection(LanguageMetadataSection.TYPE);
                if (section != null) {
                    codes.addAll(section.languages().keySet());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalStateException(
                    "no language catalogue: are the vanilla assets present?");
        }
        return codes;
    }
}

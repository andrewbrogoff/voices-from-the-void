package com.andrewbrogoff.vftv.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/** The Answerers' stats (worldgen spec, "The creatures"), fixed at registration. */
class AnswererStatsTest {
    @Test
    void theAnswerer() {
        AttributeSupplier a = Answerer.createAttributes().build();
        assertEquals(24.0, a.getBaseValue(Attributes.MAX_HEALTH));
        assertEquals(4.0, a.getBaseValue(Attributes.ATTACK_DAMAGE));
        assertEquals(2.0, a.getBaseValue(Attributes.ARMOR));
        assertEquals(0.25, a.getBaseValue(Attributes.MOVEMENT_SPEED), 1e-9);
        assertEquals(20.0, a.getBaseValue(Attributes.FOLLOW_RANGE));
        assertEquals(240, Answerer.AMBIENT_INTERVAL);
    }

    @Test
    void theCantor() {
        AttributeSupplier c = Cantor.createCantorAttributes().build();
        assertEquals(20.0, c.getBaseValue(Attributes.MAX_HEALTH));
        assertEquals(2.0, c.getBaseValue(Attributes.ATTACK_DAMAGE));
        assertEquals(0.0, c.getBaseValue(Attributes.ARMOR));
        assertEquals(0.23, c.getBaseValue(Attributes.MOVEMENT_SPEED), 1e-6);
        assertEquals(20.0, c.getBaseValue(Attributes.FOLLOW_RANGE));
    }
}

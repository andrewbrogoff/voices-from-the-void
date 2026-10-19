package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * The jar's block item (essentia spec, "The jar as an item"): a destroyed filled stack claims its
 * contents first, then spills count × contents once.
 */
public final class JarItem extends BlockItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The jar's item. */
    public JarItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        ItemStack stack = entity.getItem();
        Retained<JarStack> claimed = stack.remove(EssentiaContent.contents());
        if (claimed == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (claimed instanceof Retained.Read<JarStack> read) {
            long amount = (long) stack.getCount() * read.value().amount();
            EssentiaService.spill(
                    level,
                    entity.blockPosition(),
                    read.value().aspect(),
                    amount,
                    SpillCause.DESTROYED);
        } else {
            LOGGER.warn(
                    "A destroyed essentia jar at {} held unreadable contents",
                    entity.blockPosition());
        }
    }
}

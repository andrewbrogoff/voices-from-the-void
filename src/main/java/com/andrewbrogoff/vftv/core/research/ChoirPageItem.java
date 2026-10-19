package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.research.model.Evidence;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Choir page (research spec, "Choir pages"): its {@code vftv:page} component names its entry.
 * Using it, in either hand, tips it in: the page's {@code choir_page} trigger latches, the record
 * resolves, and the page is consumed once the latch is committed. Each case answers on the status
 * line; a page that isn't tipped in is kept, so it can be handed on.
 */
public final class ChoirPageItem extends Item {
    private static final String CHOIR_PAGES =
            ResearchKeys.CATEGORY_CHOIR_PAGES.identifier().toString();

    /** "The page is blank to me.": no component, or it names no Choir page entry. */
    public static final String BLANK = "message.vftv.choir_page.blank";

    /** "The hand is too old for me yet.": the page's Stage is above the player's. */
    public static final String TOO_OLD = "message.vftv.choir_page.too_old";

    /** "Already in my book.": the page entry is latched or learned. */
    public static final String ALREADY = "message.vftv.choir_page.already";

    /** "Tipped in.": the page is consumed. */
    public static final String TIPPED = "message.vftv.choir_page.tipped";

    /** A page. */
    public ChoirPageItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            tipIn(server, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Tips {@code stack} in for {@code player}, answering on their status line.
     *
     * @return the message key sent
     */
    public static String tipIn(ServerPlayer player, ItemStack stack) {
        ResourceKey<Research> page = stack.get(ResearchItems.page());
        String id = page == null ? null : page.identifier().toString();
        ResearchEntry entry =
                id == null
                        ? null
                        : ResearchService.data(player.level().getServer().registryAccess())
                                .entries()
                                .get(id);
        ResearchRecord record = ResearchService.record(player);
        String answer;
        if (id == null || entry == null || !entry.category().equals(CHOIR_PAGES)) {
            answer = BLANK;
        } else if (record == null) {
            answer = SharedMessages.UNREADABLE;
        } else if (entry.stage() > record.stage()) {
            answer = TOO_OLD;
        } else if (record.isLatchedOrLearned(id)) {
            answer = ALREADY;
        } else if (Resolver.evidence(player, new Evidence.ChoirPage(id))) {
            stack.shrink(1);
            player.level()
                    .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            ResearchItems.sound("tip_in"),
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F);
            answer = TIPPED;
        } else {
            answer = SharedMessages.UNREADABLE;
        }
        StatusLines.send(player, Component.translatable(answer));
        return answer;
    }
}

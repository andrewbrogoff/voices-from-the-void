package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.core.text.StatusLine;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;

/** The text feature's GameTests: the status line's server path (accessibility, R5). */
final class TextGameTests {
    private TextGameTests() {}

    /**
     * Adds the feature's tests by name; ids are listed in {@code src/gameTest/expected/text.txt}.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("text_status_line_send", TextGameTests::statusLineSend);
    }

    /**
     * {@code StatusLines.send} sends one {@code StatusLine} to that player only, carrying the
     * translatable component with its key and arguments unresolved, as owners' refusal tests
     * assert.
     */
    private static void statusLineSend(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        List<StatusLine> sent = new ArrayList<>();
        BiConsumer<ServerPlayer, StatusLine> previous =
                StatusLines.swapSender(
                        (to, line) -> {
                            helper.assertTrue(
                                    to.getUUID().equals(player.getUUID()), "sent to that player");
                            sent.add(line);
                        });
        try {
            StatusLines.send(player, Component.translatable(SharedMessages.BUSY));
            StatusLines.send(
                    player,
                    Component.translatable("status.vftv.test", Component.literal("Aer"), "12"));
        } finally {
            StatusLines.swapSender(previous);
        }
        helper.assertValueEqual(sent.size(), 2, "one line per send");
        if (!(sent.get(1).text().getContents() instanceof TranslatableContents contents)) {
            helper.fail("the text is translatable");
            return;
        }
        helper.assertValueEqual(contents.getKey(), "status.vftv.test", "the key");
        helper.assertValueEqual(contents.getArgs().length, 2, "the arguments");
        helper.assertValueEqual(
                sent.get(0).text(), Component.translatable(SharedMessages.BUSY), "busy");
        helper.succeed();
    }
}

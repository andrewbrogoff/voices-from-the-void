package com.andrewbrogoff.vftv.core.vis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/**
 * A filled Bell Jar's {@code vftv:captured_node} (wands spec, "Moving nodes: the Bell Jar"): {@code
 * {"format": 1, "token": <long>, "node": <node state>}}. The token is this capture's; only the jar
 * holding the token the registry records may release the node.
 *
 * @param token the capture token, 1 or more
 * @param node the node's settled state at capture
 */
public record CapturedNode(long token, NodeState node) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** The bounded codec: format 1, every field required, no unknown key. */
    public static final Codec<CapturedNode> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.<CapturedNode>create(
                                    i ->
                                            i.group(
                                                            Codec.INT
                                                                    .fieldOf("format")
                                                                    .forGetter(c -> FORMAT),
                                                            Codec.LONG
                                                                    .fieldOf("token")
                                                                    .forGetter(CapturedNode::token),
                                                            NodeState.BOUNDED
                                                                    .fieldOf("node")
                                                                    .forGetter(CapturedNode::node))
                                                    .apply(i, CapturedNode::decoded))
                            .validate(
                                    c ->
                                            c.token >= 1 && c.node.id() >= 1
                                                    ? DataResult.success(c)
                                                    : DataResult.error(
                                                            () -> "token or id below 1")),
                    Set.of("format", "token", "node"));

    private static CapturedNode decoded(int format, long token, NodeState node) {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
        return new CapturedNode(token, node);
    }
}

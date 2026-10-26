package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.worldgen.model.TemplateRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:template/1} converter (worldgen spec, "Templates" and "Authored content,
 * Templates"): it validates a text template under its family's rules and writes vanilla's
 * structure NBT through {@link StructureTemplate} and {@link NbtIo}, deterministically. Every
 * family shares the format, namespace, banned-block, Hush and attachment rules; a {@link Family}
 * adds its files, roles, allowed {@code vftv:} blocks, entities and chest loot tables.
 */
public final class TemplateConverter {
    private TemplateConverter() {}

    private static final Pattern POS = Pattern.compile("(0|[1-9][0-9]?),(0|[1-9][0-9]?),(0|[1-9][0-9]?)");
    private static final Pattern KEY = Pattern.compile("[!-~]");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> NAMESPACES = Set.of("minecraft", "vftv");
    private static final Set<String> NEEDS_DATA =
            Set.of("minecraft:jigsaw", "minecraft:chest", "vftv:reliquary", "vftv:node");

    private static final Schema.Obj JIGSAW =
            new Schema.Obj(
                    List.of(
                            Schema.required("name", new Schema.Id()),
                            Schema.required("target", new Schema.Id()),
                            Schema.required("pool", new Schema.Id()),
                            Schema.required("final_state", new Schema.Str(1, 256, null)),
                            Schema.required("joint", new Schema.OneOf(Set.of("aligned", "rollable"))),
                            Schema.optional("placement_priority", new Schema.Int(0, 100)),
                            Schema.optional("selection_priority", new Schema.Int(0, 100))));

    private static final Schema.Obj BLOCK_ENTITY =
            new Schema.Obj(
                    List.of(
                            Schema.optional("jigsaw", JIGSAW),
                            Schema.optional(
                                    "chest",
                                    new Schema.Obj(
                                            List.of(Schema.required("loot_table", new Schema.Id())))),
                            Schema.optional(
                                    "reliquary",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required(
                                                            "site",
                                                            new Schema.Str(3, 256, ID))))),
                            Schema.optional(
                                    "node",
                                    new Schema.Obj(List.of(Schema.required("type", new Schema.Id()))))));

    /** {@code vftv:template/1}: the common shape; the converter checks the rest. */
    public static final Schema SCHEMA =
            Schema.of(
                    "vftv:template/1",
                    Schema.required(
                            "size",
                            new Schema.ListOf(
                                    new Schema.Int(1, WorldgenLimits.MAX_TEMPLATE_SIDE), 3, 3, false)),
                    Schema.required(
                            "palette",
                            new Schema.MapOf(
                                    new Schema.Str(1, 1, KEY), new Schema.Str(1, 512, null), 1, 64)),
                    Schema.required(
                            "layers",
                            new Schema.ListOf(
                                    new Schema.ListOf(
                                            new Schema.Str(0, WorldgenLimits.MAX_TEMPLATE_SIDE, null),
                                            1,
                                            WorldgenLimits.MAX_TEMPLATE_SIDE,
                                            false),
                                    1,
                                    WorldgenLimits.MAX_TEMPLATE_SIDE,
                                    false)),
                    Schema.optional(
                            "block_entities",
                            new Schema.MapOf(
                                    new Schema.Str(5, 8, POS),
                                    BLOCK_ENTITY,
                                    0,
                                    WorldgenLimits.MAX_TEMPLATE_SIDE * 64)),
                    Schema.optional(
                            "entities",
                            new Schema.ListOf(
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required(
                                                            "pos",
                                                            new Schema.ListOf(
                                                                    new Schema.Int(
                                                                            0,
                                                                            WorldgenLimits
                                                                                            .MAX_TEMPLATE_SIDE
                                                                                    - 1),
                                                                    3,
                                                                    3,
                                                                    false)),
                                                    Schema.required("type", new Schema.Id()))),
                                    0,
                                    64,
                                    false)));

    /**
     * A template family (worldgen spec, "Families").
     *
     * @param name the family's name
     * @param files each file name and its role
     * @param vftvBlocks the {@code vftv:} blocks it allows
     * @param entities the entity types it allows
     * @param lootTables the chest loot tables it allows
     */
    public record Family(
            String name,
            Map<String, TemplateRules.Role> files,
            Set<String> vftvBlocks,
            Set<String> entities,
            Set<String> lootTables) {}

    /** The {@code choir_ruin} family: eight files, the Reliquary and the node, two Answerers. */
    public static final Family CHOIR_RUIN =
            new Family(
                    "choir_ruin",
                    TemplateRules.FILES,
                    Set.of("vftv:reliquary", "vftv:node"),
                    Set.of("vftv:answerer", "vftv:answerer_cantor"),
                    Set.of("vftv:chests/choir_ruin"));

    /**
     * A conversion.
     *
     * @param problems every rejection; empty when the template passed
     * @param nbt the structure NBT, or null when rejected
     */
    public record Result(List<String> problems, @Nullable CompoundTag nbt) {}

    /** The hushed form blocks: what {@code #vftv:hushed} holds (Hush spec, "The hushed forms"). */
    public static Set<String> hushedBlocks() {
        Set<String> out = new TreeSet<>();
        HushKeys.FORMS.forEach(f -> out.add(f.form().toString()));
        return out;
    }

    /**
     * Validates and converts one template.
     *
     * @param name its file name, without {@code .json}
     * @param json the envelope
     * @param family its family
     * @param nodeAspects a node type's aspects at their range minima, ascending id; null when the
     *     type is unknown
     */
    public static Result convert(
            String name,
            JsonObject json,
            Family family,
            Function<String, @Nullable List<NodeState.NodeAspect>> nodeAspects) {
        List<String> problems = new ArrayList<>(Schema.check(json, Map.of(SCHEMA.id(), SCHEMA)));
        if (!problems.isEmpty()) {
            return new Result(prefix(name, problems), null);
        }
        TemplateRules.Role role = family.files().get(name);
        if (role == null) {
            return new Result(List.of(name + ": not a file of the " + family.name() + " family"), null);
        }
        JsonArray size = json.getAsJsonArray("size");
        int sx = size.get(0).getAsInt();
        int sy = size.get(1).getAsInt();
        int sz = size.get(2).getAsInt();
        // the palette
        Map<Character, BlockState> palette = new TreeMap<>();
        Set<String> hushed = hushedBlocks();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("palette").entrySet()) {
            char key = e.getKey().charAt(0);
            String text = e.getValue().getAsString();
            String where = "palette." + e.getKey() + ": ";
            if (key == ' ') {
                problems.add(where + "a space is structure void, never a key");
                continue;
            }
            BlockState state = parse(text, where, problems);
            if (state != null) {
                blockRules(state, where, family, hushed, problems);
                palette.put(key, state);
            }
        }
        // the layers
        JsonArray layers = json.getAsJsonArray("layers");
        char[][][] cells = new char[sy][sz][sx];
        if (layers.size() != sy) {
            problems.add("layers: " + layers.size() + " layers, not " + sy);
        }
        for (int y = 0; y < Math.min(sy, layers.size()); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            if (rows.size() != sz) {
                problems.add("layers[" + y + "]: " + rows.size() + " rows, not " + sz);
            }
            for (int z = 0; z < Math.min(sz, rows.size()); z++) {
                String row = rows.get(z).getAsString();
                if (row.length() != sx) {
                    problems.add(
                            "layers[" + y + "][" + z + "]: " + row.length() + " columns, not " + sx);
                }
                for (int x = 0; x < sx; x++) {
                    char c = x < row.length() ? row.charAt(x) : ' ';
                    if (c != ' ' && !palette.containsKey(c)) {
                        problems.add(
                                "layers[" + y + "][" + z + "][" + x + "]: '" + c + "' is not in the palette");
                        c = ' ';
                    }
                    cells[y][z][x] = c;
                }
            }
        }
        if (!problems.isEmpty()) {
            return new Result(prefix(name, problems), null);
        }
        // the block entities
        JsonObject blockEntities =
                json.has("block_entities") ? json.getAsJsonObject("block_entities") : new JsonObject();
        Map<Long, CompoundTag> data = new TreeMap<>();
        List<TemplateRules.Jigsaw> jigsaws = new ArrayList<>();
        List<TemplateRules.Faced> reliquaries = new ArrayList<>();
        List<TemplateRules.Pos> nodes = new ArrayList<>();
        int chests = 0;
        for (Map.Entry<String, JsonElement> e : blockEntities.entrySet()) {
            String where = "block_entities." + e.getKey() + ": ";
            String[] p = e.getKey().split(",", -1);
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            if (x >= sx || y >= sy || z >= sz) {
                problems.add(where + "outside the size");
                continue;
            }
            JsonObject body = e.getValue().getAsJsonObject();
            if (body.size() != 1) {
                problems.add(where + "exactly one of jigsaw, chest, reliquary and node");
                continue;
            }
            String kind = body.keySet().iterator().next();
            JsonObject fields = body.getAsJsonObject(kind);
            char c = cells[y][z][x];
            BlockState state = c == ' ' ? null : at(palette, c);
            String block = state == null ? "structure void" : id(state.getBlock());
            String expected =
                    switch (kind) {
                        case "jigsaw" -> "minecraft:jigsaw";
                        case "chest" -> "minecraft:chest";
                        case "reliquary" -> "vftv:reliquary";
                        default -> "vftv:node";
                    };
            if (state == null || !block.equals(expected)) {
                problems.add(where + "a " + kind + " on " + block);
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("id", expected);
            switch (kind) {
                case "jigsaw" -> {
                    tag.putString("name", fields.get("name").getAsString());
                    tag.putString("target", fields.get("target").getAsString());
                    tag.putString("pool", fields.get("pool").getAsString());
                    tag.putString("final_state", fields.get("final_state").getAsString());
                    tag.putString("joint", fields.get("joint").getAsString());
                    tag.putInt("placement_priority", intOr(fields, "placement_priority"));
                    tag.putInt("selection_priority", intOr(fields, "selection_priority"));
                    String orientation =
                            state.getValue(BlockStateProperties.ORIENTATION).getSerializedName();
                    String[] parts = orientation.split("_", -1);
                    jigsaws.add(
                            new TemplateRules.Jigsaw(
                                    x,
                                    y,
                                    z,
                                    parts[0],
                                    parts[1],
                                    new TemplateRules.Fields(
                                            fields.get("name").getAsString(),
                                            fields.get("target").getAsString(),
                                            fields.get("pool").getAsString()),
                                    fields.get("final_state").getAsString(),
                                    fields.get("joint").getAsString()));
                    if (intOr(fields, "placement_priority") != 0
                            || intOr(fields, "selection_priority") != 0) {
                        problems.add(where + "a connector's priorities are 0");
                    }
                }
                case "chest" -> {
                    String table = fields.get("loot_table").getAsString();
                    if (!family.lootTables().contains(table)) {
                        problems.add(where + "the loot table must be one of " + family.lootTables());
                    }
                    tag.putString("LootTable", table);
                    chests++;
                }
                case "reliquary" -> {
                    tag.putInt("format", 1);
                    tag.putString("site", fields.get("site").getAsString());
                    reliquaries.add(
                            new TemplateRules.Faced(
                                    x,
                                    y,
                                    z,
                                    state.getValue(HorizontalDirectionalBlock.FACING)
                                            .getSerializedName()));
                }
                default -> {
                    String type = fields.get("type").getAsString();
                    if (type.equals(HushKeys.HUSHED_NODE.identifier().toString())) {
                        problems.add(where + "a node of type " + type);
                        continue;
                    }
                    List<NodeState.NodeAspect> aspects = nodeAspects.apply(type);
                    if (aspects == null) {
                        problems.add(where + "unknown node type " + type);
                        continue;
                    }
                    tag.put("node", nodeRecord(Identifier.parse(type), aspects));
                    nodes.add(new TemplateRules.Pos(x, y, z));
                }
            }
            data.put(BlockPos.asLong(x, y, z), tag);
        }
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    char c = cells[y][z][x];
                    if (c != ' '
                            && NEEDS_DATA.contains(id(at(palette, c).getBlock()))
                            && !data.containsKey(BlockPos.asLong(x, y, z))) {
                        problems.add(
                                "(" + x + ", " + y + ", " + z + "): "
                                        + id(at(palette, c).getBlock())
                                        + " needs its block entity");
                    }
                }
            }
        }
        // the entities
        List<TemplateRules.Entity> entities = new ArrayList<>();
        ListTag entityList = new ListTag();
        JsonArray entityJson = json.has("entities") ? json.getAsJsonArray("entities") : new JsonArray();
        for (int i = 0; i < entityJson.size(); i++) {
            JsonObject e = entityJson.get(i).getAsJsonObject();
            JsonArray pos = e.getAsJsonArray("pos");
            int x = pos.get(0).getAsInt();
            int y = pos.get(1).getAsInt();
            int z = pos.get(2).getAsInt();
            String type = e.get("type").getAsString();
            if (!family.entities().contains(type)) {
                problems.add("entities[" + i + "]: " + type + " is not one of " + family.entities());
                continue;
            }
            if (x >= sx || y >= sy || z >= sz) {
                problems.add("entities[" + i + "]: outside the size");
                continue;
            }
            entities.add(new TemplateRules.Entity(x, y, z, type.endsWith("_cantor")));
            CompoundTag entity = new CompoundTag();
            entity.put("pos", doubles(x + 0.5, y, z + 0.5));
            entity.put("blockPos", ints(x, y, z));
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", type);
            entity.put("nbt", nbt);
            entityList.add(entity);
        }
        if (!problems.isEmpty()) {
            return new Result(prefix(name, problems), null);
        }
        problems.addAll(
                TemplateRules.check(
                        new TemplateRules.Template(
                                role,
                                sx,
                                sy,
                                sz,
                                new TemplateRules.Cells() {
                                    @Override
                                    public boolean air(int x, int y, int z) {
                                        char c = cells[y][z][x];
                                        return c != ' ' && at(palette, c).isAir();
                                    }

                                    @Override
                                    public boolean sturdy(int x, int y, int z) {
                                        char c = cells[y][z][x];
                                        return c != ' '
                                                && at(palette, c)
                                                        .isFaceSturdy(
                                                                EmptyBlockGetter.INSTANCE,
                                                                BlockPos.ZERO,
                                                                Direction.UP);
                                    }
                                },
                                jigsaws,
                                reliquaries,
                                nodes,
                                chests,
                                entities)));
        if (!problems.isEmpty()) {
            return new Result(prefix(name, problems), null);
        }
        // vanilla's structure format, normalised through StructureTemplate
        List<Character> keys = new ArrayList<>(palette.keySet());
        ListTag paletteTag = new ListTag();
        keys.forEach(k -> paletteTag.add(NbtUtils.writeBlockState(at(palette, k))));
        ListTag blocks = new ListTag();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    char c = cells[y][z][x];
                    if (c == ' ') {
                        continue;
                    }
                    CompoundTag b = new CompoundTag();
                    b.put("pos", ints(x, y, z));
                    b.putInt("state", keys.indexOf(c));
                    CompoundTag nbt = data.get(BlockPos.asLong(x, y, z));
                    if (nbt != null) {
                        b.put("nbt", nbt);
                    }
                    blocks.add(b);
                }
            }
        }
        CompoundTag raw = new CompoundTag();
        raw.put("size", ints(sx, sy, sz));
        raw.put("palette", paletteTag);
        raw.put("blocks", blocks);
        raw.put("entities", entityList);
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, raw);
        return new Result(List.of(), template.save(new CompoundTag()));
    }

    /** The complete template record of a node of {@code type} (spec, "The saved template record"). */
    static net.minecraft.nbt.Tag nodeRecord(Identifier type, List<NodeState.NodeAspect> aspects) {
        return NodeState.BOUNDED
                .encodeStart(
                        NbtOps.INSTANCE,
                        new NodeState(
                                0,
                                NodeState.Origin.STRUCTURE,
                                type,
                                NodeState.Clarity.STEADY,
                                aspects,
                                new NodeState.RegenClock(0, 1, 1),
                                NodeState.Cultivation.NONE))
                .getOrThrow();
    }

    /** The gzipped bytes vanilla's template manager reads. */
    public static byte[] gzip(CompoundTag nbt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(nbt, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static @Nullable BlockState parse(String text, String where, List<String> problems) {
        BlockStateParser.BlockResult result;
        try {
            result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false);
        } catch (CommandSyntaxException e) {
            problems.add(where + "does not parse: " + e.getMessage());
            return null;
        }
        BlockState state = result.blockState();
        if (result.properties().size() != state.getProperties().size()) {
            Set<String> given = new HashSet<>();
            result.properties().keySet().forEach(p -> given.add(p.getName()));
            Set<String> all = new TreeSet<>();
            state.getProperties().forEach(p -> all.add(p.getName()));
            all.removeAll(given);
            problems.add(where + "every property given exactly once; missing " + all);
            return null;
        }
        return state;
    }

    private static void blockRules(
            BlockState state, String where, Family family, Set<String> hushed, List<String> problems) {
        Block block = state.getBlock();
        String id = id(block);
        String ns = id.substring(0, id.indexOf(':'));
        if (!NAMESPACES.contains(ns)) {
            problems.add(where + id + ": only the minecraft and vftv namespaces");
        }
        if (ns.equals("vftv") && !family.vftvBlocks().contains(id)) {
            problems.add(where + id + " is not a vftv block the " + family.name() + " family allows");
        }
        if (hushed.contains(id)) {
            problems.add(where + id + " is in #vftv:hushed");
        }
        if (state.is(Blocks.STRUCTURE_BLOCK)
                || block instanceof SpawnerBlock
                || block instanceof TrialSpawnerBlock
                || block instanceof VaultBlock) {
            problems.add(where + id + " is never allowed in a template");
        } else if (block instanceof EntityBlock entity && !state.is(Blocks.CHEST)) {
            BlockEntity be = entity.newBlockEntity(BlockPos.ZERO, state);
            if (be instanceof Container) {
                problems.add(where + id + ": the only container allowed is a chest");
            }
        }
    }

    private static BlockState at(Map<Character, BlockState> palette, char c) {
        return java.util.Objects.requireNonNull(palette.get(c), "a palette key");
    }

    private static int intOr(JsonObject fields, String key) {
        return fields.has(key) ? fields.get(key).getAsInt() : 0;
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int v : values) {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }

    private static ListTag doubles(double... values) {
        ListTag list = new ListTag();
        for (double v : values) {
            list.add(DoubleTag.valueOf(v));
        }
        return list;
    }

    private static List<String> prefix(String name, List<String> problems) {
        return problems.stream().map(p -> name + ": " + p).toList();
    }
}

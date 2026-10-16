package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The Road's layout in {@link EldritchKeys#VUULAS}: four stations stacked on one vertical axis
 * through the centre of block (0, 0), each a ring of masonry around an open well. Constants only;
 * the Road's templates are checked against them. See the Eldritch tier spec, "The layout".
 *
 * <p>Station-relative positions: a cell (x, y, z) is the block at (x, floor + y, z), where floor is
 * the station's floor y; the {@code _Y} constants of a cell are such offsets above the floor, and
 * {@code d2} is x squared plus z squared.
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later release changes ours.
 */
@Stable
public final class RoadLayout {
    private RoadLayout() {}

    /** The floor y of Step 1, in blocks: 256. */
    public static final int STEP_1_FLOOR_Y = 256;

    /** The floor y of Step 2, in blocks: 192. */
    public static final int STEP_2_FLOOR_Y = 192;

    /** The floor y of Step 3, in blocks: 128. */
    public static final int STEP_3_FLOOR_Y = 128;

    /** The floor y of the Heart's station, in blocks: 64. */
    public static final int HEART_FLOOR_Y = 64;

    /** Each station template's size along x, in blocks: 47. */
    public static final int TEMPLATE_SIZE_X = 47;

    /** Each station template's size along y, in blocks: 40. */
    public static final int TEMPLATE_SIZE_Y = 40;

    /** Each station template's size along z, in blocks: 47. */
    public static final int TEMPLATE_SIZE_Z = 47;

    /** Each station template's origin x, in blocks: -23. */
    public static final int TEMPLATE_ORIGIN_X = -23;

    /** Each station template's origin y relative to its floor, in blocks: -8. */
    public static final int TEMPLATE_ORIGIN_Y = -8;

    /** Each station template's origin z, in blocks: -23. */
    public static final int TEMPLATE_ORIGIN_Z = -23;

    /** The well: every cell with {@code d2} at most this, at every height, is air: 36. */
    public static final int WELL_MAX_D2 = 36;

    /** The ring's smallest {@code d2}: 37. Ring floor cells are at y 0, standing cells at y 1. */
    public static final int RING_MIN_D2 = 37;

    /** The ring's largest {@code d2}: 441. */
    public static final int RING_MAX_D2 = 441;

    /** The landing cell's x: 0. A player arrives at its bottom centre. */
    public static final int LANDING_X = 0;

    /** The landing cell's y above the floor: 1. */
    public static final int LANDING_Y = 1;

    /** The landing cell's z: -17. */
    public static final int LANDING_Z = -17;

    /** The arrival yaw ({@code yRot}), in degrees, facing the well: 0. */
    public static final int LANDING_YAW = 0;

    /** The Step Bell's x at Steps 1 to 3: 0. */
    public static final int STEP_BELL_X = 0;

    /** The Step Bell's y above the floor, at every station: 1. */
    public static final int STEP_BELL_Y = 1;

    /** The Step Bell's z at Steps 1 to 3: 17. */
    public static final int STEP_BELL_Z = 17;

    /** The Step Bell's x at the Heart's station: 17. */
    public static final int HEART_STEP_BELL_X = 17;

    /** The Step Bell's z at the Heart's station: 0. */
    public static final int HEART_STEP_BELL_Z = 0;

    /** The rise cell's x at Steps 1 to 3: 0. The rise point is its bottom centre. */
    public static final int RISE_X = 0;

    /** The rise cell's y above the floor: 1. */
    public static final int RISE_Y = 1;

    /** The rise cell's z: 12. */
    public static final int RISE_Z = 12;

    /** The rise box's smallest x, inclusive: -1. The rise box is all air. */
    public static final int RISE_BOX_MIN_X = -1;

    /** The rise box's largest x, inclusive: 1. */
    public static final int RISE_BOX_MAX_X = 1;

    /** The rise box's lowest y above the floor, inclusive: 1. */
    public static final int RISE_BOX_MIN_Y = 1;

    /** The rise box's highest y above the floor, inclusive: 5. */
    public static final int RISE_BOX_MAX_Y = 5;

    /** The rise box's smallest z, inclusive: 11. */
    public static final int RISE_BOX_MIN_Z = 11;

    /** The rise box's largest z, inclusive: 13. */
    public static final int RISE_BOX_MAX_Z = 13;

    /** The choir walk's smallest {@code d2}, at Steps 1 to 3: 81. The choir walk is all air. */
    public static final int CHOIR_WALK_MIN_D2 = 81;

    /** The choir walk's largest {@code d2}: 225. */
    public static final int CHOIR_WALK_MAX_D2 = 225;

    /** The choir walk's lowest y above the floor, inclusive: 1. */
    public static final int CHOIR_WALK_MIN_Y = 1;

    /** The choir walk's highest y above the floor, inclusive: 5. */
    public static final int CHOIR_WALK_MAX_Y = 5;

    /** The Reliquary's x at Steps 1 to 3: -17. It faces east. */
    public static final int RELIQUARY_X = -17;

    /** The Reliquary's y above the floor: 1. */
    public static final int RELIQUARY_Y = 1;

    /** The Reliquary's z: 0. */
    public static final int RELIQUARY_Z = 0;

    /** The Heart block's x, at the Heart's station only: 0. */
    public static final int HEART_X = 0;

    /** The Heart block's y above the floor: 1. */
    public static final int HEART_Y = 1;

    /** The Heart block's z: 17. */
    public static final int HEART_Z = 17;

    /** The arena's horizontal radius about the axis point (0.5, 0.5), in blocks, inclusive: 22. */
    public static final int ARENA_RADIUS = 22;

    /** The arena's lowest player y relative to the floor, inclusive, in blocks: -2. */
    public static final int ARENA_MIN_Y = -2;

    /** The arena's player y bound relative to the floor, exclusive, in blocks: 12. */
    public static final int ARENA_MAX_Y = 12;

    /** The Road's column: the largest absolute x and z of any of its blocks: 23. */
    public static final int COLUMN_HALF_WIDTH = 23;

    /** The Road's column: its lowest y, inclusive: 56. */
    public static final int COLUMN_MIN_Y = 56;

    /** The Road's column: its y bound, exclusive: 296. */
    public static final int COLUMN_MAX_Y = 296;
}

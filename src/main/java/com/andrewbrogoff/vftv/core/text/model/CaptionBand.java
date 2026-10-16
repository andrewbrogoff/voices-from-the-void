package com.andrewbrogoff.vftv.core.text.model;

/**
 * The caption band (accessibility, "The caption band"): the status line and the Voice text share
 * one bounded strip above the vanilla action bar, which crosshair cards keep clear of. The client
 * updates it once per frame, before any card layout, and publishes {@link #bandTop}, {@link
 * #showing} and a {@link #revision} that rises whenever an input changes. Updating allocates
 * nothing.
 *
 * <p>Also holds the shared transient-text timing ("How long transient text stays") and the status
 * line's visible-line rule (R5).
 */
public final class CaptionBand {
    /** A text line's height. */
    public static final int LINE = 9;

    /** A block's height beyond its lines: 2 px of padding above and below. */
    public static final int PADDING = 4;

    /** The gap between the Voice block and the status line, and below the band. */
    public static final int GAP = 8;

    /** The status line's visible-line cap. */
    public static final int STATUS_MAX_LINES = 3;

    /** The Voice text's visible-line cap. */
    public static final int VOICE_MAX_LINES = 4;

    /** The status line's base lifetime, in ticks, before the notification-time multiplier. */
    public static final int STATUS_TICKS = 60;

    /** The fade at the end of a transient text's lifetime, in ticks. */
    public static final int FADE_TICKS = 20;

    private int guiHeight = -1;
    private int leftHeight;
    private int rightHeight;
    private int statusLines;
    private int statusSerial;
    private int voiceLines;
    private int voiceSerial;

    private int revision;
    private boolean showing;
    private int bottom;
    private int statusTop;
    private int voiceBottom;
    private int bandTop;
    private float voiceScale = 1;

    /**
     * Recomputes the band from its inputs; a change to any of them raises the revision.
     *
     * @param guiHeight the scaled screen height
     * @param leftHeight vanilla's left status-row height, final for the frame
     * @param rightHeight vanilla's right status-row height
     * @param statusLines the status line's visible lines, 0 when none shows (or hidden)
     * @param statusSerial a counter the status line bumps on arrival, replacement and expiry
     * @param voiceLines the Voice text's lines, 0 when none shows (or hidden); capped at 4
     * @param voiceSerial a counter the Voice text bumps on arrival, replacement and expiry
     * @return whether the revision rose
     */
    public boolean update(
            int guiHeight,
            int leftHeight,
            int rightHeight,
            int statusLines,
            int statusSerial,
            int voiceLines,
            int voiceSerial) {
        if (guiHeight == this.guiHeight
                && leftHeight == this.leftHeight
                && rightHeight == this.rightHeight
                && statusLines == this.statusLines
                && statusSerial == this.statusSerial
                && voiceLines == this.voiceLines
                && voiceSerial == this.voiceSerial) {
            return false;
        }
        this.guiHeight = guiHeight;
        this.leftHeight = leftHeight;
        this.rightHeight = rightHeight;
        this.statusLines = statusLines;
        this.statusSerial = statusSerial;
        this.voiceLines = voiceLines;
        this.voiceSerial = voiceSerial;
        revision++;

        int s = Math.clamp(statusLines, 0, STATUS_MAX_LINES);
        int v = Math.clamp(voiceLines, 0, VOICE_MAX_LINES);
        int base = guiHeight - Math.max(Math.max(leftHeight, rightHeight) + 9, 68);
        bottom = base - GAP;
        statusTop = s > 0 ? bottom - (PADDING + LINE * s) : bottom;
        voiceBottom = s > 0 ? statusTop - GAP : bottom;
        int voiceHeight = v > 0 ? PADDING + LINE * v : 0;
        int top = v > 0 ? voiceBottom - voiceHeight : statusTop;
        int limit = Math.max(0, guiHeight) / 3;
        showing = s > 0 || v > 0;
        voiceScale = 1;
        if (top < limit) {
            top = limit;
            if (v > 0) {
                voiceScale = Math.max(0, voiceBottom - limit) / (float) voiceHeight;
            }
        }
        bandTop = top;
        return true;
    }

    /** Rises whenever an input changes; every card rebuilds its layout when it does. */
    public int revision() {
        return revision;
    }

    /** Whether a status line or a Voice line shows. */
    public boolean showing() {
        return showing;
    }

    /** The top of the highest block showing, never above ⌊H / 3⌋; the bottom when none shows. */
    public int bandTop() {
        return bandTop;
    }

    /** The band's bottom, 8 px above the vanilla action-bar baseline: the status line's bottom. */
    public int bottom() {
        return bottom;
    }

    /** The status line's top edge (the bottom when none shows). */
    public int statusTop() {
        return statusTop;
    }

    /** Where the Voice block's bottom edge goes. */
    public int voiceBottom() {
        return voiceBottom;
    }

    /** The pose scale that keeps the Voice block below ⌊H / 3⌋; 1 unless the rows are very tall. */
    public float voiceScale() {
        return voiceScale;
    }

    /**
     * A crosshair card's usable height {@code H′}: the screen height with no caption showing, else
     * {@code bandTop − 2}.
     */
    public int usableHeight(int guiHeight) {
        return showing ? bandTop - 2 : guiHeight;
    }

    /**
     * How long transient text stays: {@code ceil(base × max(1, notificationDisplayTime))} ticks, an
     * intentional minimum of 1 on vanilla's multiplier.
     */
    public static int hold(int baseTicks, double notificationDisplayTime) {
        // In hundredths, so 1.1 × 60 is 66 and not 67: the option holds decimal steps.
        long hundredths = Math.max(100, Math.round(notificationDisplayTime * 100));
        return (int) Math.ceilDiv(baseTicks * hundredths, 100);
    }

    /**
     * The opacity of transient text with {@code remaining} ticks left: it fades over the last 20.
     */
    public static float fade(int remaining, float partialTick) {
        return Math.clamp((remaining - partialTick) / FADE_TICKS, 0f, 1f);
    }

    /** The status line's visible lines for a text that wraps to {@code wrapped} lines. */
    public static int statusVisibleLines(int wrapped) {
        return Math.min(wrapped, STATUS_MAX_LINES);
    }

    /**
     * Whether a status text that wraps to {@code wrapped} lines shows only its first 2, the second
     * ending in "…", and the {@code more} hint naming the details key.
     */
    public static boolean statusTruncated(int wrapped) {
        return wrapped > STATUS_MAX_LINES;
    }
}

package com.andrewbrogoff.vftv.core.infusion;

/**
 * An altar's readout status (infusion spec, "Statuses"), first match in declaration order. Each has
 * its HUD line-1 key {@code status.vftv.infusion.<name>}; {@code ENCHANT_REFUSED} appends its
 * reason.
 */
public enum InfusionStatus {
    /** The bowl's record is unreadable. */
    UNREADABLE,
    /** The bowl has a run: the readout is the run's. */
    RINGING,
    /** A box chunk isn't loaded or block-ticking. */
    UNLOADED,
    /** No centre item. */
    EMPTY,
    /** More than 16 pedestals at the bowl's level. */
    TOO_MANY,
    /** A pedestal at the bowl's level is unreadable. */
    PEDESTAL_UNREADABLE,
    /** A pedestal has a live or unknown claim of another bowl. */
    PROMISED,
    /** The bowl or a pedestal is transaction-busy. */
    BUSY,
    /** No learned recipe matches. */
    NO_RECIPE,
    /** An enchanting candidate matched centre and components and failed a condition. */
    ENCHANT_REFUSED,
    /** Ready to start. */
    READY;

    /** The status's line-1 key; {@code reason} is the enchanting refusal's, else null. */
    public String key(@org.jspecify.annotations.Nullable EnchantRefusal reason) {
        String base = InfusionLang.STATUS + name().toLowerCase(java.util.Locale.ROOT);
        return this == ENCHANT_REFUSED && reason != null
                ? InfusionLang.STATUS
                        + "enchant."
                        + reason.name().toLowerCase(java.util.Locale.ROOT)
                : base;
    }

    /** Why an enchanting candidate refused (infusion spec, "Enchanting through infusion"). */
    public enum EnchantRefusal {
        /** It can't store enchantments, or is in {@code #vftv:infusion_unenchantable}. */
        UNSTORABLE,
        /** It doesn't support the enchantment. */
        UNSUPPORTED,
        /** It is already at the cap. */
        AT_MAX,
        /** It holds a conflicting enchantment. */
        CONFLICT
    }
}

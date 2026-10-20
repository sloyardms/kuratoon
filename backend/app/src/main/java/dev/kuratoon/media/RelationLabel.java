package dev.kuratoon.media;

/** How a related media is labelled when viewed from the other media. */
public enum RelationLabel {
    SEQUEL,
    PREQUEL,
    SIDE_STORY,
    MAIN_STORY,
    SPIN_OFF,
    ORIGINAL;

    /** Label of {@code to} when viewing {@code from} (the stored direction). */
    public static RelationLabel forward(RelationType type) {
        return switch (type) {
            case SEQUEL -> SEQUEL;
            case SIDE_STORY -> SIDE_STORY;
            case SPIN_OFF -> SPIN_OFF;
        };
    }

    /** Label of {@code from} when viewing {@code to}. */
    public static RelationLabel inverse(RelationType type) {
        return switch (type) {
            case SEQUEL -> PREQUEL;
            case SIDE_STORY -> MAIN_STORY;
            case SPIN_OFF -> ORIGINAL;
        };
    }
}

package dev.kuratoon.media;

/**
 * Stored relation types. {@code to} is a SEQUEL / SIDE_STORY / SPIN_OFF <em>of</em> {@code from};
 * the inverse labels are derived (see {@link RelationLabel}).
 */
public enum RelationType {
    SEQUEL,
    SIDE_STORY,
    SPIN_OFF
}

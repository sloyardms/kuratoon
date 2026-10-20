package dev.kuratoon.config;

/** How image URLs are delivered (see {@code docs/spec/06-images.md}). */
public enum ImageAccess {
    /** nginx {@code secure_link} signed URLs (default). */
    SIGNED,
    /** Unsigned URLs, for LAN/VPN-only installs. */
    PUBLIC
}

package dev.kuratoon.plugin.api;

import java.net.URI;
import org.jsoup.nodes.Document;

/**
 * A metadata parser plugin. The core fetches pages; plugins only parse them (see
 * {@code docs/spec/03-plugins-fetching.md}). Implementations are discovered with
 * {@link java.util.ServiceLoader} and must not perform any I/O.
 */
public interface MetadataProvider {
    /** Stable unique id, e.g. "example". */
    String id();

    String displayName();

    /** Plugin API version this plugin was built against. */
    int apiVersion();

    /** True if this plugin can parse pages for this URL. */
    boolean supports(URI url);

    /** Parse a fetched or saved page. Must not perform I/O. */
    MediaMetadata parse(Document html, URI sourceUrl) throws ParseException;
}

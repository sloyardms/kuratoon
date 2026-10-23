package dev.kuratoon.ingestion;

import dev.kuratoon.media.MediaFields;
import java.nio.file.Path;

/**
 * Where metadata comes from when the manifest is not complete: a plugin parsing the fetched page
 * or the saved {@code source.html} (docs/spec/03-plugins-fetching.md, phase 4).
 */
public interface MetadataSource {

    sealed interface Result permits Found, NotAvailable {}

    /** @param fields scraped values; manifest values still override them */
    record Found(MediaFields fields, String pluginId) implements Result {}

    /** @param message shown to the admin; the job needs attention */
    record NotAvailable(String message) implements Result {}

    /**
     * @param url normalized URL, or null when only the saved page exists
     * @param sourceHtml the saved page, or null
     */
    Result lookup(String url, Path sourceHtml);
}

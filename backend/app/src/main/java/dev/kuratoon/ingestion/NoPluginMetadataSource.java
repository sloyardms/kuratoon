package dev.kuratoon.ingestion;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Phase 3: no plugins exist yet, so no URL can be resolved to metadata. Jobs that need a plugin go
 * to NEEDS_ATTENTION, where the admin fills metadata manually. Replaced by plugin loading and
 * fetching in phase 4.
 */
@Component
class NoPluginMetadataSource implements MetadataSource {

    @Override
    public Result lookup(String url, Path sourceHtml) {
        String what = url != null ? url : "the saved page (source.html)";
        return new NotAvailable("No metadata plugin supports " + what
                + ". Plugins are not available yet: fill in metadata manually.");
    }
}

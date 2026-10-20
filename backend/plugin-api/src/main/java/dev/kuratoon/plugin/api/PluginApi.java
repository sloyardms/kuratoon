package dev.kuratoon.plugin.api;

/** Version information for the plugin API. */
public final class PluginApi {
    /**
     * Major version of this API. Plugins return it from {@link MetadataProvider#apiVersion()}; the
     * core rejects plugins built for a different major version.
     */
    public static final int VERSION = 1;

    private PluginApi() {}
}

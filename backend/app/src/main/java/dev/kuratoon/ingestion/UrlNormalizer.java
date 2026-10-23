package dev.kuratoon.ingestion;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Normalized metadata URL, used as {@code media.source_url} to match updates: lowercase scheme and
 * host, no default port, no fragment, no tracking parameters, no trailing slash.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING_PARAMS =
            Set.of("fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "_ga", "yclid");

    private UrlNormalizer() {}

    /** Empty when the text is not an absolute http(s) URL with a host. */
    public static Optional<String> normalize(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            return Optional.empty();
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);

        StringBuilder out = new StringBuilder(scheme).append("://").append(uri.getHost().toLowerCase(Locale.ROOT));
        if (!defaultPort) {
            out.append(':').append(port);
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        out.append(path);

        String query = uri.getRawQuery();
        if (query != null) {
            List<String> kept = new ArrayList<>();
            for (String param : query.split("&")) {
                String name = param.split("=", 2)[0].toLowerCase(Locale.ROOT);
                if (!param.isEmpty() && !name.startsWith("utm_") && !TRACKING_PARAMS.contains(name)) {
                    kept.add(param);
                }
            }
            if (!kept.isEmpty()) {
                out.append('?').append(String.join("&", kept));
            }
        }
        return Optional.of(out.toString());
    }
}

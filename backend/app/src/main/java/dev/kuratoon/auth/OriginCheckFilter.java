package dev.kuratoon.auth;

import dev.kuratoon.config.KuratoonProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence next to SameSite=Lax (ADR 0013): every non-GET request must carry an Origin header
 * equal to the APP_BASE_URL origin. A request without Origin is allowed only if it carries no auth
 * cookies (tools and tests using a Bearer header cannot be forged by a browser).
 */
class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final String expectedOrigin;
    private final ProblemResponses problems;

    OriginCheckFilter(KuratoonProperties properties, ProblemResponses problems) {
        this.expectedOrigin = properties.appOrigin();
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || isAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }
        problems.write(response, HttpStatus.FORBIDDEN,
                "Cross-origin request rejected. Use the app at its configured address (APP_BASE_URL).");
    }

    private boolean isAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return !AuthCookies.hasAuthCookie(request);
        }
        return expectedOrigin.equals(origin.strip().toLowerCase(Locale.ROOT));
    }
}

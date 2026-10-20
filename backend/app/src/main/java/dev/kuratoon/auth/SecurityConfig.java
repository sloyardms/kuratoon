package dev.kuratoon.auth;

import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.user.AppUserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless API security (docs/spec/04-auth-users.md, ADR 0013). Access tokens come from the
 * {@code kuratoon_access} cookie or a Bearer header; every non-GET request passes the Origin check.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Endpoints that never need (or read) an access token. */
    private static final RequestMatcher PUBLIC = new OrRequestMatcher(List.of(
            PATHS.matcher(HttpMethod.GET, "/api/v1/health"),
            PATHS.matcher(HttpMethod.GET, "/actuator/health"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/auth/status"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/logout"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/setup"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/register"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/password-reset/request"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/password-reset/confirm")));

    static boolean isPublicEndpoint(HttpServletRequest request) {
        return PUBLIC.matches(request);
    }

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            KuratoonProperties properties,
            ProblemResponses problems)
            throws Exception {
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(JwtService.CLAIM_ROLE))));

        http.csrf(csrf -> csrf.disable()) // SameSite=Lax + OriginCheckFilter (ADR 0013)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new AccessTokenResolver())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authentication))
                        .authenticationEntryPoint((req, res, e) ->
                                problems.write(res, HttpStatus.UNAUTHORIZED, "Authentication required."))
                        .accessDeniedHandler((req, res, e) ->
                                problems.write(res, HttpStatus.FORBIDDEN, "You do not have access to this resource.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                problems.write(res, HttpStatus.UNAUTHORIZED, "Authentication required."))
                        .accessDeniedHandler((req, res, ex) ->
                                problems.write(res, HttpStatus.FORBIDDEN, "You do not have access to this resource.")))
                .addFilterBefore(new OriginCheckFilter(properties, problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwtService, AppUserRepository users, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtService.key())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new TokenVersionValidator(users)));
        return decoder;
    }

    /** Argon2id (spec 04 "Passwords"). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

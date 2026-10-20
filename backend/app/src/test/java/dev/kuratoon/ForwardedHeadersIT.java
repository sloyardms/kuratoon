package dev.kuratoon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.auth.JwtService;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import dev.kuratoon.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client IP spoofing: the backend must honour X-Forwarded-For only from the compose network
 * ({@code INTERNAL_SUBNET}), never from arbitrary private addresses (docs/spec/08-deployment.md).
 * Tests connect from 127.0.0.1, so it plays the role of "a LAN client" or "nginx" depending on
 * whether it is inside the configured subnet.
 */
class ForwardedHeadersIT {

    private static final String SPOOFED = "203.0.113.7";

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "INTERNAL_SUBNET=172.30.71.0/24")
    @Import({TestcontainersConfiguration.class, RemoteAddrEndpoint.class})
    @ActiveProfiles("test")
    class FromOutsideInternalSubnet {

        @LocalServerPort
        int port;

        @Autowired
        AppUserRepository users;

        @Autowired
        JwtService jwt;

        @Test
        void spoofedForwardedForIsIgnored() throws Exception {
            assertThat(remoteAddr(port, SPOOFED, token(users, jwt))).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, RemoteAddrEndpoint.class})
    @ActiveProfiles("test")
    class WithoutInternalSubnet {

        @LocalServerPort
        int port;

        @Autowired
        AppUserRepository users;

        @Autowired
        JwtService jwt;

        @Test
        void noProxyIsTrusted() throws Exception {
            assertThat(remoteAddr(port, SPOOFED, token(users, jwt))).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "INTERNAL_SUBNET=127.0.0.0/8")
    @Import({TestcontainersConfiguration.class, RemoteAddrEndpoint.class})
    @ActiveProfiles("test")
    class FromInsideInternalSubnet {

        @LocalServerPort
        int port;

        @Autowired
        AppUserRepository users;

        @Autowired
        JwtService jwt;

        @Test
        void forwardedForFromNginxIsHonoured() throws Exception {
            assertThat(remoteAddr(port, SPOOFED, token(users, jwt))).isEqualTo(SPOOFED);
        }
    }

    /** A logged-in user's token: the endpoint is under /api/v1, which needs authentication. */
    private static String token(AppUserRepository users, JwtService jwt) {
        AppUser user = users.save(new AppUser("fwd" + System.nanoTime() % 1_000_000, null, "unused-hash", Role.USER));
        return jwt.issue(user).value();
    }

    private static String remoteAddr(int port, String forwardedFor, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/test/remote-addr"))
                .header("X-Forwarded-For", forwardedFor)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class RemoteAddrEndpoint {

        @GetMapping("/api/v1/test/remote-addr")
        String remoteAddr(HttpServletRequest request) {
            return request.getRemoteAddr();
        }
    }
}

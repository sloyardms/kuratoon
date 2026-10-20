package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import dev.kuratoon.support.TestData;
import dev.kuratoon.user.Role;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Self-service password reset with a real SMTP server (GreenMail). The "no SMTP → 404" case is in
 * {@link SetupAndRegistrationIT}, which runs without SMTP.
 */
class PasswordResetIT extends AuthTestBase {

    private static final Pattern LINK = Pattern.compile("http://localhost:8080/reset-password\\?token=(\\S+)");

    /** One server for the class: the Spring context reads its (dynamic) port only once. */
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("kuratoon.smtp.host", () -> "127.0.0.1");
        registry.add("kuratoon.smtp.port", () -> greenMail.getSmtp().getPort());
        registry.add("kuratoon.smtp.from", () -> "kuratoon@example.test");
    }

    @Autowired
    MailStatus mail;

    @BeforeEach
    void smtpVerified() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        mail.verify();
        assertThat(mail.available()).isTrue();
        asAnonymous();
    }

    @Test
    void statusReportsPasswordResetEnabled() {
        assertThat(get("/api/v1/auth/status")).bodyJson().extractingPath("$.passwordResetEnabled").isEqualTo(true);
    }

    @Test
    void existingAndUnknownEmailsGetIdenticalResponses() throws Exception {
        MvcTestResult existing = request("admin@example.test");
        MvcTestResult unknown = request("nobody@example.test");

        assertThat(existing).hasStatus(202);
        assertThat(unknown).hasStatus(202);
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(existing.getResponse().getContentAsString());
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(greenMail.getReceivedMessages()[0].getAllRecipients()[0].toString()).isEqualTo("admin@example.test");
    }

    @Test
    void resetLinkWorksOnceAndEndsAllSessions() throws Exception {
        String oldRefresh = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        String token = requestToken("ADMIN@example.test");

        assertThat(confirm(token, "brand-new-password")).hasStatus(204);

        assertThat(login("admin", TestData.PASSWORD)).hasStatus(401);
        assertThat(login("admin", "brand-new-password")).hasStatusOk();
        assertThat(refresh(oldRefresh)).hasStatus(401);
        assertThat(jdbc.queryForObject("select token_version from app_user where username = 'admin'", Integer.class))
                .isEqualTo(1);
        assertThat(confirm(token, "another-password-1")).hasStatus(400);
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        String token = requestToken("admin@example.test");
        clock.advance(Duration.ofMinutes(31));

        assertThat(confirm(token, "brand-new-password")).hasStatus(400);
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        String token = requestToken("admin@example.test");

        assertThat(confirm(token, "short")).hasStatus(400);
        assertThat(confirm(token, "long-enough-now")).hasStatus(204);
    }

    @Test
    void disabledUsersGetNoMail() throws Exception {
        var user = data.aUser("quiet", Role.USER);
        jdbc.update("update app_user set enabled = false where id = ?", user.getId());

        assertThat(request("quiet@example.test")).hasStatus(202);
        assertThat(greenMail.waitForIncomingEmail(1000, 1)).isFalse();
    }

    private MvcTestResult request(String email) {
        return browser(mvc.post().uri("/api/v1/auth/password-reset/request"), "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}")
                .exchange();
    }

    private MvcTestResult confirm(String token, String password) {
        return browser(mvc.post().uri("/api/v1/auth/password-reset/confirm"), "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"password\": \"" + password + "\"}")
                .exchange();
    }

    private String requestToken(String email) throws Exception {
        assertThat(request(email)).hasStatus(202);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        // getContent() decodes the transfer encoding (long lines are quoted-printable soft-wrapped).
        String body = (String) greenMail.getReceivedMessages()[0].getContent();
        Matcher link = LINK.matcher(body);
        assertThat(link.find()).as("reset link in mail: %s", body).isTrue();
        return URLDecoder.decode(link.group(1), StandardCharsets.UTF_8);
    }
}

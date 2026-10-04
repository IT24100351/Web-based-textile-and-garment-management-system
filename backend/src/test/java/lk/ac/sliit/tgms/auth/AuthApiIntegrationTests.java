package lk.ac.sliit.tgms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM users");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void customerCanRegisterLoginUseSessionAndLogout() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Registered Customer",
                                  "email": "Customer@Example.com",
                                  "password": "secure-pass-123",
                                  "role": "ADMINISTRATOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully."))
                .andExpect(jsonPath("$.user.email").value("customer@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        var stored = jdbcTemplate.queryForMap(
                "SELECT id, password_hash, role FROM users WHERE email = ?", "customer@example.com");
        assertThat(stored.get("password_hash")).isNotEqualTo("secure-pass-123");
        assertThat(stored.get("role")).isEqualTo("CUSTOMER");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "secure-pass-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AuthCookieService.COOKIE_NAME, true))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        var sessionJwt = jwtDecoder.decode(sessionCookie.getValue());
        assertThat(sessionJwt.getSubject())
                .isEqualTo(String.valueOf(((Number) stored.get("id")).longValue()));
        assertThat(sessionJwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(sessionJwt.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "iat", "exp", "sub", "role");

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"));

        mockMvc.perform(get("/api/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("customer@example.com"));

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").isEmpty());
    }

    @Test
    void invalidCredentialsReturnOneSafeError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password."));
    }

    @Test
    void registrationValidationAndDuplicateEmailAreClear() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.fullName").exists())
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.password").exists());

        String registration = """
                {"fullName":"Customer","email":"customer@example.com","password":"secure-pass-123"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void publicHealthPageRemainsAvailableWithoutSession() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}

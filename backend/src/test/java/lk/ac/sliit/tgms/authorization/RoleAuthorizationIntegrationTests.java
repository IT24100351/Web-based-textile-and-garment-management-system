package lk.ac.sliit.tgms.authorization;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class RoleAuthorizationIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest(name = "{0} may access {2}")
    @MethodSource("roleEndpoints")
    void eachSupportedRoleCanAccessItsOwnFunction(
            UserRole role, UserRole deniedRole, String path) throws Exception {
        mockMvc.perform(get(path).cookie(sessionFor(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.message").value("Role access confirmed."));
    }

    @ParameterizedTest(name = "{1} may not access the {0} function")
    @MethodSource("roleEndpoints")
    void authenticatedWrongRoleIsForbidden(
            UserRole role, UserRole deniedRole, String path) throws Exception {
        mockMvc.perform(get(path).cookie(sessionFor(deniedRole)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message")
                        .value("You do not have permission to perform this action."));
    }

    @ParameterizedTest(name = "anonymous request to {2} is unauthorized")
    @MethodSource("roleEndpoints")
    void missingSessionIsUnauthorized(
            UserRole role, UserRole deniedRole, String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private Cookie sessionFor(UserRole role) {
        UserAccount account = new UserAccount(
                100L, "role@example.com", "not-used", "Role Test User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private static Stream<Arguments> roleEndpoints() {
        return Stream.of(
                Arguments.of(
                        UserRole.ADMINISTRATOR,
                        UserRole.SUPPLIER,
                        "/api/role-access/administrator"),
                Arguments.of(
                        UserRole.SUPPLIER,
                        UserRole.INVENTORY_MANAGER,
                        "/api/role-access/supplier"),
                Arguments.of(
                        UserRole.INVENTORY_MANAGER,
                        UserRole.PRODUCTION_MANAGER,
                        "/api/role-access/inventory-manager"),
                Arguments.of(
                        UserRole.PRODUCTION_MANAGER,
                        UserRole.SALES_OFFICER,
                        "/api/role-access/production-manager"),
                Arguments.of(
                        UserRole.SALES_OFFICER,
                        UserRole.ADMINISTRATOR,
                        "/api/role-access/sales-officer"));
    }
}

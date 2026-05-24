package com.project.clinic;

import com.jayway.jsonpath.JsonPath;
import com.project.clinic.entity.Account;
import com.project.clinic.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clinic-test;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ClinicManagementSystemApplicationTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder passwords;

    @Test
    void loginUsesJpaAndProtectsTheSession() throws Exception {
        Account account = new Account();
        account.setUsername("reception");
        account.setEmail("reception@example.test");
        account.setRole(Account.Role.RECEPTIONIST);
        account.setActive(true);
        account.setPassword("legacy-password");
        accounts.save(account);

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session)
                        .param("login", account.getEmail()).param("password", "legacy-password")
                        .header("X-CSRF-TOKEN", csrf(session)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()));
        mvc.perform(get("/api/admin/employees").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/profile/me").session(session).param("accountId", "99999"))
                .andExpect(status().isForbidden());

        String changePassword = """
                {"currentPassword":"legacy-password","newPassword":"new-password"}
                """;
        mvc.perform(put("/api/profile/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(changePassword))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/profile/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(changePassword)
                        .header("X-CSRF-TOKEN", csrf(session)))
                .andExpect(status().isOk());
        String storedPassword = accounts.findById(account.getId()).orElseThrow().getPassword();
        assertThat(storedPassword).isNotEqualTo("new-password");
        assertThat(passwords.matches("new-password", storedPassword)).isTrue();

        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", csrf(session)))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    private String csrf(MockHttpSession session) throws Exception {
        String response = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }
}

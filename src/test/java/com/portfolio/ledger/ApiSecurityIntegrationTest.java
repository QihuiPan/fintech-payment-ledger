package com.portfolio.ledger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,info,metrics,prometheus")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enforcesResourceAndOperationsBoundaries() throws Exception {
        String adminWallet = createWallet("ledger-admin", "admin-demo", "admin-owner@example.com");
        String userWallet = createWallet("wallet-user", "wallet-demo", "wallet-owner@example.com");

        mockMvc.perform(get("/api/wallets/{walletId}/balances", adminWallet)
                        .with(httpBasic("wallet-user", "wallet-demo")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/wallets/{walletId}/balances", userWallet)
                        .with(httpBasic("wallet-user", "wallet-demo")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/wallets/{walletId}/balances", userWallet)
                        .with(httpBasic("ledger-admin", "admin-demo")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")
                        .with(httpBasic("wallet-user", "wallet-demo")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")
                        .with(httpBasic("ledger-admin", "admin-demo")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/dev/h2-console"))
                .andExpect(status().isNotFound());
    }

    private String createWallet(String username, String password, String email) throws Exception {
        String response = mockMvc.perform(post("/api/wallets")
                        .with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","currencies":["GBP","EUR"]}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("walletId").asText();
    }
}

package com.acme.ems.security;

import com.acme.ems.EmployeeManagementApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EmployeeManagementApplication.class)
@AutoConfigureMockMvc
class HardeningWebTest {
  @Autowired MockMvc mvc;

  @Test void configuredCorsOriginPassesPreflight() throws Exception {
    mvc.perform(options("/api/auth/login")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test void unconfiguredCorsOriginIsRejected() throws Exception {
    mvc.perform(options("/api/auth/login")
            .header("Origin", "https://evil.example")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test void trailingJsonIsRejected() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"} garbage"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test void unicodePasswordExceedingBcryptByteLimitIsRejected() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@example.com\",\"password\":\"" + "\u00e9".repeat(40) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void extremeJoiningDateIsRejectedBeforeDatabaseAccess() throws Exception {
    mvc.perform(post("/api/admin/employees").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"employeeId":"BOUNDARY","firstName":"Boundary","lastName":"Test",
                 "email":"boundary@example.com","password":"Welcome123!","jobTitle":"Engineer",
                 "department":"Engineering","role":"EMPLOYEE","dateOfJoining":"-999999999-01-01"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void stringBooleanIsRejected() throws Exception {
    mvc.perform(patch("/api/admin/employees/1/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":\"false\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test void oversizedBodyIsRejected() throws Exception {
    String body = "{\"email\":\"nobody@example.com\",\"password\":\"" + "x".repeat(1_048_576) + "\"}";
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void missingRouteAndUnsupportedMethodHaveSpecificErrors() throws Exception {
    mvc.perform(get("/api/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    mvc.perform(get("/api/auth/login"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test void repeatedLoginFailuresAreRateLimited() throws Exception {
    String body = "{\"email\":\"rate-limit@example.com\",\"password\":\"password123\"}";
    for (int attempt = 0; attempt < 5; attempt++) {
      mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
  }
}

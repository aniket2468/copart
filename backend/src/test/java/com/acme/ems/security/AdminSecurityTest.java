package com.acme.ems.security;
import com.acme.ems.EmployeeManagementApplication;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.security.test.context.support.WithMockUser;import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(classes=EmployeeManagementApplication.class) @AutoConfigureMockMvc
class AdminSecurityTest{
 @Autowired MockMvc mvc;
 @Test @WithMockUser(username="admin@example.com",roles="ADMIN") void adminCanAccess()throws Exception{mvc.perform(get("/api/admin/dashboard")).andExpect(status().isOk());}
 @Test @WithMockUser(username="sarah@example.com",roles="MANAGER") void managerIsForbidden()throws Exception{mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());}
 @Test @WithMockUser(username="john@example.com",roles="EMPLOYEE") void employeeIsForbidden()throws Exception{mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());}
 @Test void unauthenticatedIsUnauthorized()throws Exception{mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());}
}

package com.acme.ems.controller;

import com.acme.ems.dto.AuthDtos.LoginRequest;
import com.acme.ems.model.*;
import com.acme.ems.security.JwtService;
import com.acme.ems.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.User;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
  @Test void successfulLoginReturnsTokenAndSafeUser(){
    AuthenticationManager auth=mock(AuthenticationManager.class);JwtService jwt=mock(JwtService.class);EmployeeService employees=mock(EmployeeService.class);
    var principal=User.withUsername("john@example.com").password("hash").roles("EMPLOYEE").build();
    when(auth.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));when(jwt.create(principal)).thenReturn("signed.jwt");
    Employee e=new Employee();e.setId(2L);e.setEmployeeId("EMP-001");e.setFirstName("John");e.setLastName("Carter");e.setEmail("john@example.com");e.setPasswordHash("never-return-this");e.setJobTitle("Engineer");e.setDepartment("Engineering");e.setRole(Role.EMPLOYEE);e.setDateOfJoining(LocalDate.of(2024,1,15));e.setActive(true);when(employees.current("john@example.com")).thenReturn(e);
    var result=new AuthController(auth,jwt,employees).login(new LoginRequest("john@example.com","Welcome123!"));
    assertEquals("signed.jwt",result.token());assertEquals("john@example.com",result.user().email());assertFalse(result.toString().contains("never-return-this"));
  }
}

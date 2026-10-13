package com.acme.ems.service;
import com.acme.ems.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import static org.junit.jupiter.api.Assertions.*;
class JwtServiceTest{
  @Test void createsAndValidatesLoginToken(){JwtService jwt=new JwtService("a-secure-test-key-that-is-at-least-thirty-two-characters",60000);var user=User.withUsername("john@example.com").password("hash").roles("EMPLOYEE").build();String token=jwt.create(user);assertEquals("john@example.com",jwt.subject(token));assertTrue(jwt.valid(token,user));}
}

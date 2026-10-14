package com.acme.ems.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.HandlerExceptionResolver;
import io.jsonwebtoken.MalformedJwtException;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
  @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

  @Test void dependencyFailureIsResolvedAndDoesNotBecomeUnauthenticated() throws Exception {
    JwtService jwt = mock(JwtService.class);
    UserDetailsService users = mock(UserDetailsService.class);
    HandlerExceptionResolver errors = mock(HandlerExceptionResolver.class);
    FilterChain chain = mock(FilterChain.class);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    request.addHeader("Authorization", "Bearer valid-token");
    when(jwt.subject("valid-token")).thenReturn("user@example.com");
    var failure = new DataAccessResourceFailureException("Database unavailable");
    when(users.loadUserByUsername("user@example.com")).thenThrow(failure);
    new JwtAuthenticationFilter(jwt, users, errors).doFilter(request, response, chain);
    verify(errors).resolveException(request, response, null, failure);
    verifyNoInteractions(chain);
  }

  @Test void malformedTokenContinuesToAuthenticationEntryPoint() throws Exception {
    JwtService jwt = mock(JwtService.class);
    UserDetailsService users = mock(UserDetailsService.class);
    HandlerExceptionResolver errors = mock(HandlerExceptionResolver.class);
    FilterChain chain = mock(FilterChain.class);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    request.addHeader("Authorization", "Bearer malformed");
    when(jwt.subject("malformed")).thenThrow(new MalformedJwtException("Bad token"));
    new JwtAuthenticationFilter(jwt, users, errors).doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    verifyNoInteractions(errors, users);
  }
}

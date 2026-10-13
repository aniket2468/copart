package com.acme.ems.controller;
import com.acme.ems.dto.AuthDtos.*;import com.acme.ems.security.JwtService;import com.acme.ems.service.*;
import jakarta.validation.Valid;
import org.springframework.security.authentication.*;import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController{
  private final AuthenticationManager auth;private final JwtService jwt;private final EmployeeService employees;
  public AuthController(AuthenticationManager auth,JwtService jwt,EmployeeService employees){this.auth=auth;this.jwt=jwt;this.employees=employees;}
  @PostMapping("/login") LoginResponse login(@Valid @RequestBody LoginRequest r){var a=auth.authenticate(new UsernamePasswordAuthenticationToken(r.email(),r.password()));return new LoginResponse(jwt.create((UserDetails)a.getPrincipal()),EmployeeMapper.toResponse(employees.current(r.email())));}
}


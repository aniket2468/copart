package com.acme.ems.controller;
import com.acme.ems.dto.AuthDtos.*;import com.acme.ems.security.JwtService;import com.acme.ems.security.LoginAttemptService;import com.acme.ems.service.*;import com.acme.ems.exception.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController{
  private static final Logger log=LoggerFactory.getLogger(AuthController.class);
  private final AuthenticationManager auth;private final JwtService jwt;private final EmployeeService employees;private final LoginAttemptService attempts;
  public AuthController(AuthenticationManager auth,JwtService jwt,EmployeeService employees,LoginAttemptService attempts){this.auth=auth;this.jwt=jwt;this.employees=employees;this.attempts=attempts;}
  @PostMapping("/login") LoginResponse login(@Valid @RequestBody LoginRequest r,HttpServletRequest request){String remote=request.getRemoteAddr();attempts.check(r.email(),remote);try{var a=auth.authenticate(new UsernamePasswordAuthenticationToken(r.email(),r.password()));attempts.succeeded(r.email(),remote);return new LoginResponse(jwt.create((UserDetails)a.getPrincipal()),EmployeeMapper.toResponse(employees.current(r.email())));}catch(InternalAuthenticationServiceException ex){AuthenticationException accountFailure=accountFailure(ex);if(accountFailure!=null){attempts.failed(r.email(),remote);throw accountFailure;}attempts.succeeded(r.email(),remote);String requestId=(String)request.getAttribute(RequestIdFilter.ATTRIBUTE);log.error("Authentication dependency failure requestId={}",requestId,ex);throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","Authentication is temporarily unavailable");}catch(AuthenticationException ex){attempts.failed(r.email(),remote);throw ex;}}
  private AuthenticationException accountFailure(Throwable failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof AccountStatusException status)return status;return null;}
}


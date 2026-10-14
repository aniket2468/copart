package com.acme.ems.security;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter{
  private final JwtService jwt; private final UserDetailsService users;
  private final HandlerExceptionResolver errors;
  public JwtAuthenticationFilter(JwtService jwt,UserDetailsService users,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors){this.jwt=jwt;this.users=users;this.errors=errors;}
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    String h=req.getHeader(HttpHeaders.AUTHORIZATION);
    if(h!=null&&h.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){String token=h.substring(7);try{String email=jwt.subject(token);UserDetails u=users.loadUserByUsername(email);if(jwt.valid(token,u)){UsernamePasswordAuthenticationToken a=new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities());a.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));SecurityContextHolder.getContext().setAuthentication(a);}}catch(JwtException|IllegalArgumentException|AuthenticationException invalidCredentials){SecurityContextHolder.clearContext();}catch(Exception failure){errors.resolveException(req,res,null,failure);return;}}
    chain.doFilter(req,res);
  }
}


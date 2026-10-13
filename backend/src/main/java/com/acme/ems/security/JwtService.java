package com.acme.ems.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JwtService {
  private final SecretKey key; private final long expiration;
  public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.expiration-ms}") long expiration){this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.expiration=expiration;}
  public String create(UserDetails user){Date now=new Date();return Jwts.builder().subject(user.getUsername()).issuedAt(now).expiration(new Date(now.getTime()+expiration)).signWith(key).compact();}
  public String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
  public boolean valid(String token,UserDetails user){try{return subject(token).equals(user.getUsername());}catch(JwtException|IllegalArgumentException e){return false;}}
}


package com.acme.ems.config;
import com.acme.ems.repository.EmployeeRepository;
import com.acme.ems.security.JwtAuthenticationFilter;
import com.acme.ems.exception.ApiErrorResponse;
import com.acme.ems.exception.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.*;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration @EnableMethodSecurity
public class SecurityConfig{
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
  @Bean UserDetailsService userDetailsService(EmployeeRepository repo){return email->{var e=repo.findByEmailIgnoreCase(email).orElseThrow(()->new UsernameNotFoundException("Account not found"));if(!e.isActive())throw new DisabledException("Account is deactivated");return User.withUsername(e.getEmail()).password(e.getPasswordHash()).roles(e.getRole().name()).disabled(!e.isActive()).build();};}
  @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
  @Bean SecurityFilterChain filter(HttpSecurity h,JwtAuthenticationFilter jwt,RequestIdFilter requestIds,ObjectMapper mapper)throws Exception{return h.csrf(c->c.disable()).cors(withDefaults()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->writeError(req,res,mapper,401,"AUTHENTICATION_REQUIRED","Authentication is required")).accessDeniedHandler((req,res,ex)->writeError(req,res,mapper,403,"ACCESS_DENIED","Access denied"))).authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/error").permitAll().requestMatchers(HttpMethod.OPTIONS,"/**").permitAll().anyRequest().authenticated()).addFilterBefore(requestIds,UsernamePasswordAuthenticationFilter.class).addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();}
  @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(origin->!origin.isEmpty()).toList());c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setMaxAge(3600L);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
  private void writeError(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,ObjectMapper mapper,int status,String code,String message)throws java.io.IOException{String requestId=(String)req.getAttribute(RequestIdFilter.ATTRIBUTE);res.setStatus(status);res.setCharacterEncoding("UTF-8");res.setContentType("application/json");mapper.writeValue(res.getOutputStream(),new ApiErrorResponse(Instant.now(),status,code,message,Map.of(),requestId));}
}

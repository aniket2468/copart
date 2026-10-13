package com.acme.ems.exception;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException ex){return body(ex.status(),ex.getMessage(),Map.of());}
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException ex){
    Map<String,String> fields=new LinkedHashMap<>(); ex.getBindingResult().getFieldErrors().forEach(e->fields.put(e.getField(),e.getDefaultMessage()));
    return body(HttpStatus.BAD_REQUEST,"Validation failed",fields);
  }
  @ExceptionHandler(AuthenticationException.class) ResponseEntity<?> authentication(AuthenticationException ex){return body(HttpStatus.UNAUTHORIZED,"Invalid email or password",Map.of());}
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(AccessDeniedException ex){return body(HttpStatus.FORBIDDEN,"Access denied",Map.of());}
  @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception ex){return body(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred",Map.of());}
  private ResponseEntity<?> body(HttpStatus status,String message,Map<String,String> fields){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now(),"status",status.value(),"error",status.getReasonPhrase(),"message",message,"fields",fields));}
}

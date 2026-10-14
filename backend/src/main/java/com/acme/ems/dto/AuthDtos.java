package com.acme.ems.dto;
import jakarta.validation.constraints.*;
public final class AuthDtos {
  private AuthDtos(){}
  public record LoginRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password){
    @AssertTrue(message="Password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinByteLimit(){return password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72;}
  }
  public record LoginResponse(String token,EmployeeDtos.EmployeeResponse user){}
}


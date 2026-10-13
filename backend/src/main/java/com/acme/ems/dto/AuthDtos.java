package com.acme.ems.dto;
import jakarta.validation.constraints.*;
public final class AuthDtos {
  private AuthDtos(){}
  public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
  public record LoginResponse(String token,EmployeeDtos.EmployeeResponse user){}
}


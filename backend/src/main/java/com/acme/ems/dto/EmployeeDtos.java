package com.acme.ems.dto;
import com.acme.ems.model.*;
import jakarta.validation.constraints.*;
import java.time.*;

public final class EmployeeDtos {
  private EmployeeDtos(){}
  public record ManagerSummary(Long id,String employeeId,String name,String email){}
  public record EmployeeResponse(Long id,String employeeId,String firstName,String lastName,String email,String jobTitle,String department,Role role,ManagerSummary manager,LocalDate dateOfJoining,boolean active,Instant createdAt){}
  public record EmployeeRequest(@NotBlank @Size(max=50) String employeeId,@NotBlank @Size(max=100) String firstName,@NotBlank @Size(max=100) String lastName,@NotBlank @Email @Size(max=254) String email,
    @Size(min=8,max=72) String password,@NotBlank @Size(max=100) String jobTitle,@NotBlank @Size(max=100) String department,@NotNull Role role,Long managerId,@NotNull @PastOrPresent LocalDate dateOfJoining){
      @AssertTrue(message="Joining date must use a year from 1 through 9999")
      public boolean isJoiningDateSupported(){return dateOfJoining==null||(dateOfJoining.getYear()>=1&&dateOfJoining.getYear()<=9999);}
      @AssertTrue(message="Password must not exceed 72 UTF-8 bytes")
      public boolean isPasswordWithinByteLimit(){return password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72;}
    }
  public record StatusRequest(@NotNull Boolean active){}
}


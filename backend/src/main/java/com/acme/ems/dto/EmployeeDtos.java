package com.acme.ems.dto;
import com.acme.ems.model.*;
import jakarta.validation.constraints.*;
import java.time.*;

public final class EmployeeDtos {
  private EmployeeDtos(){}
  public record ManagerSummary(Long id,String employeeId,String name,String email){}
  public record EmployeeResponse(Long id,String employeeId,String firstName,String lastName,String email,String jobTitle,String department,Role role,ManagerSummary manager,LocalDate dateOfJoining,boolean active,Instant createdAt){}
  public record EmployeeRequest(@NotBlank String employeeId,@NotBlank String firstName,@NotBlank String lastName,@NotBlank @Email String email,
    @Size(min=8) String password,@NotBlank String jobTitle,@NotBlank String department,@NotNull Role role,Long managerId,@NotNull @PastOrPresent LocalDate dateOfJoining){}
  public record StatusRequest(boolean active){}
}


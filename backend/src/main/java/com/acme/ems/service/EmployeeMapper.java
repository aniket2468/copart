package com.acme.ems.service;
import com.acme.ems.dto.EmployeeDtos.*;
import com.acme.ems.model.Employee;
public final class EmployeeMapper {
  private EmployeeMapper(){}
  public static EmployeeResponse toResponse(Employee e){
    ManagerSummary manager=e.getManager()==null?null:new ManagerSummary(e.getManager().getId(),e.getManager().getEmployeeId(),e.getManager().fullName(),e.getManager().getEmail());
    return new EmployeeResponse(e.getId(),e.getEmployeeId(),e.getFirstName(),e.getLastName(),e.getEmail(),e.getJobTitle(),e.getDepartment(),e.getRole(),manager,e.getDateOfJoining(),e.isActive(),e.getCreatedAt());
  }
}


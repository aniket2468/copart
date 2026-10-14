package com.acme.ems.dto;
import com.acme.ems.model.*;
import jakarta.validation.constraints.*;
import java.time.*;

public final class LeaveDtos {
  private LeaveDtos(){}
  public record LeaveCreateRequest(@NotNull LeaveType leaveType,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotBlank @Size(max=1000) String reason){}
  public record ReviewRequest(@Size(max=1000) String comment){}
  public record RejectionRequest(@NotBlank @Size(max=1000) String comment){}
  public record EmployeeSummary(Long id,String employeeId,String name,String jobTitle,String department){}
  public record LeaveResponse(Long id,EmployeeSummary employee,LeaveType leaveType,LocalDate startDate,LocalDate endDate,long days,String reason,LeaveStatus status,String managerComment,Instant requestedAt,Instant reviewedAt,String reviewedBy){}
  public record CalendarEvent(Long id,Long employeeId,String employeeName,LeaveType leaveType,LocalDate startDate,LocalDate endDate,LeaveStatus status){}
  public record ConflictDay(LocalDate date,java.util.List<String> employees){}
  public record ConflictResponse(int conflictingEmployees,java.util.List<ConflictDay> days){}
}

package com.acme.ems.dto;
import com.acme.ems.model.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public final class AdminDtos{
  private AdminDtos(){}
  public record DashboardResponse(long totalEmployees,long activeEmployees,long managers,long pendingLeaveRequests,long employeesCurrentlyOnLeave){}
  public record ManagerChangeRequest(Long managerId){}
  public record RoleChangeRequest(@NotNull Role role){}
  public record AuditResponse(Long id,Long actorId,String actorName,AuditAction action,String entityType,Long entityId,String description,Instant timestamp){}
}

package com.acme.ems.dto;
import com.acme.ems.model.LeaveType;
import jakarta.validation.constraints.*;
public final class BalanceDtos{
  private BalanceDtos(){}
  public record BalanceResponse(Long id,LeaveType leaveType,int totalAllocated,int used,int remaining,int year){}
  public record BalanceAdjustmentRequest(@Min(0) @Max(366) int totalAllocated,@NotBlank @Size(max=500) String reason){}
}


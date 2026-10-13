package com.acme.ems.model;

import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="leave_requests")
public class LeaveRequest {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) private Employee employee;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private LeaveType leaveType;
  @Column(nullable=false) private LocalDate startDate;
  @Column(nullable=false) private LocalDate endDate;
  @Column(nullable=false,length=1000) private String reason;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private LeaveStatus status=LeaveStatus.PENDING;
  @Column(length=1000) private String managerComment;
  @Column(nullable=false,updatable=false) private Instant requestedAt;
  private Instant reviewedAt;
  @ManyToOne(fetch=FetchType.LAZY) private Employee reviewedBy;
  @PrePersist void create(){requestedAt=Instant.now();}
  public Long getId(){return id;} public void setId(Long v){id=v;}
  public Employee getEmployee(){return employee;} public void setEmployee(Employee v){employee=v;}
  public LeaveType getLeaveType(){return leaveType;} public void setLeaveType(LeaveType v){leaveType=v;}
  public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;}
  public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;}
  public String getReason(){return reason;} public void setReason(String v){reason=v;}
  public LeaveStatus getStatus(){return status;} public void setStatus(LeaveStatus v){status=v;}
  public String getManagerComment(){return managerComment;} public void setManagerComment(String v){managerComment=v;}
  public Instant getRequestedAt(){return requestedAt;} public Instant getReviewedAt(){return reviewedAt;} public void setReviewedAt(Instant v){reviewedAt=v;}
  public Employee getReviewedBy(){return reviewedBy;} public void setReviewedBy(Employee v){reviewedBy=v;}
}

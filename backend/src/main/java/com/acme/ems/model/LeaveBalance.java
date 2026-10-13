package com.acme.ems.model;
import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="leave_balances",uniqueConstraints=@UniqueConstraint(name="uk_leave_balance_employee_type_year",columnNames={"employee_id","leave_type","balance_year"}))
public class LeaveBalance {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) private Employee employee;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private LeaveType leaveType;
  @Column(nullable=false) private int totalAllocated;
  @Column(nullable=false) private int used;
  @Column(name="balance_year",nullable=false) private int year;
  @Column(nullable=false,updatable=false) private Instant createdAt;
  @Column(nullable=false) private Instant updatedAt;
  @PrePersist void create(){createdAt=updatedAt=Instant.now();} @PreUpdate void update(){updatedAt=Instant.now();}
  public Long getId(){return id;} public void setId(Long v){id=v;} public Employee getEmployee(){return employee;} public void setEmployee(Employee v){employee=v;}
  public LeaveType getLeaveType(){return leaveType;} public void setLeaveType(LeaveType v){leaveType=v;} public int getTotalAllocated(){return totalAllocated;} public void setTotalAllocated(int v){totalAllocated=v;}
  public int getUsed(){return used;} public void setUsed(int v){used=v;} public int getYear(){return year;} public void setYear(int v){year=v;} public int remaining(){return totalAllocated-used;}
  public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}

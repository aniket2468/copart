package com.acme.ems.model;

import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="employees", uniqueConstraints={@UniqueConstraint(columnNames="employee_id"),@UniqueConstraint(columnNames="email")})
public class Employee {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Version private long version;
  @Column(name="employee_id",nullable=false,unique=true) private String employeeId;
  @Column(nullable=false) private String firstName;
  @Column(nullable=false) private String lastName;
  @Column(nullable=false,unique=true) private String email;
  @Column(nullable=false) private String passwordHash;
  @Column(nullable=false) private String jobTitle;
  @Column(nullable=false) private String department;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role=Role.EMPLOYEE;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="manager_id") private Employee manager;
  @Column(nullable=false) private LocalDate dateOfJoining;
  @Column(nullable=false) private boolean active=true;
  @Column(nullable=false,updatable=false) private Instant createdAt;
  @Column(nullable=false) private Instant updatedAt;
  @PrePersist void create(){createdAt=updatedAt=Instant.now(); email=email.toLowerCase();}
  @PreUpdate void update(){updatedAt=Instant.now(); email=email.toLowerCase();}
  public Long getId(){return id;} public void setId(Long v){id=v;}
  public String getEmployeeId(){return employeeId;} public void setEmployeeId(String v){employeeId=v;}
  public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
  public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
  public String getEmail(){return email;} public void setEmail(String v){email=v;}
  public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
  public String getJobTitle(){return jobTitle;} public void setJobTitle(String v){jobTitle=v;}
  public String getDepartment(){return department;} public void setDepartment(String v){department=v;}
  public Role getRole(){return role;} public void setRole(Role v){role=v;}
  public Employee getManager(){return manager;} public void setManager(Employee v){manager=v;}
  public LocalDate getDateOfJoining(){return dateOfJoining;} public void setDateOfJoining(LocalDate v){dateOfJoining=v;}
  public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
  public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
  public String fullName(){return firstName+" "+lastName;}
  public long getVersion(){return version;}
}


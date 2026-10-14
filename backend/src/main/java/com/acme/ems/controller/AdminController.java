package com.acme.ems.controller;
import com.acme.ems.dto.AdminDtos.*;import com.acme.ems.dto.BalanceDtos.*;import com.acme.ems.dto.EmployeeDtos.*;import com.acme.ems.model.*;import com.acme.ems.service.*;
import jakarta.validation.Valid;import org.springframework.data.domain.*;import org.springframework.format.annotation.DateTimeFormat;import org.springframework.http.HttpStatus;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;
import java.time.Instant;import java.util.*;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminController{
  private final AdminService admin;private final EmployeeService employees;public AdminController(AdminService a,EmployeeService e){admin=a;employees=e;}
  @GetMapping("/dashboard") DashboardResponse dashboard(){return admin.dashboard();}
  @GetMapping("/employees") List<EmployeeResponse> all(){return employees.all();}
  @GetMapping("/employees/{id}") EmployeeResponse one(@PathVariable Long id){return employees.one(id);}
  @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED) EmployeeResponse create(Authentication a,@Valid @RequestBody EmployeeRequest r){return employees.create(r,a.getName());}
  @PutMapping("/employees/{id}") EmployeeResponse update(Authentication a,@PathVariable Long id,@Valid @RequestBody EmployeeRequest r){return employees.update(id,r,a.getName());}
  @PatchMapping("/employees/{id}/status") EmployeeResponse status(Authentication a,@PathVariable Long id,@Valid @RequestBody StatusRequest r){return employees.status(id,Boolean.TRUE.equals(r.active()),a.getName());}
  @PatchMapping("/employees/{id}/manager") EmployeeResponse manager(Authentication a,@PathVariable Long id,@RequestBody ManagerChangeRequest r){return employees.changeManager(id,r.managerId(),a.getName());}
  @PatchMapping("/employees/{id}/role") EmployeeResponse role(Authentication a,@PathVariable Long id,@Valid @RequestBody RoleChangeRequest r){return employees.changeRole(id,r.role(),a.getName());}
  @GetMapping("/employees/{id}/leave-balances") List<BalanceResponse> balances(@PathVariable Long id){return admin.balances(id);}
  @PatchMapping("/employees/{id}/leave-balances/{type}") BalanceResponse adjust(Authentication a,@PathVariable Long id,@PathVariable LeaveType type,@Valid @RequestBody BalanceAdjustmentRequest r){return admin.adjust(id,type,r,a.getName());}
  @GetMapping("/audit-logs") Page<AuditResponse> logs(@RequestParam(required=false) Long actorId,@RequestParam(required=false) AuditAction action,@RequestParam(required=false) String entityType,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,@PageableDefault(size=20,sort="timestamp",direction=Sort.Direction.DESC) Pageable pageable){return admin.auditLogs(actorId,action,entityType,from,to,pageable);}
}

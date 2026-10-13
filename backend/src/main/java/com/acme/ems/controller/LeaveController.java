package com.acme.ems.controller;
import com.acme.ems.dto.LeaveDtos.*;import com.acme.ems.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/leaves")
public class LeaveController{
  private final LeaveService service;private final com.acme.ems.service.LeaveBalanceService balances;private final com.acme.ems.service.EmployeeService employees;public LeaveController(LeaveService s,com.acme.ems.service.LeaveBalanceService b,com.acme.ems.service.EmployeeService e){service=s;balances=b;employees=e;}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) LeaveResponse create(Authentication a,@Valid @RequestBody LeaveCreateRequest r){return service.create(a.getName(),r);}
  @GetMapping("/me") List<LeaveResponse> mine(Authentication a){return service.mine(a.getName());}
  @GetMapping("/{id}") LeaveResponse one(Authentication a,@PathVariable Long id){return service.one(a.getName(),id);}
  @PatchMapping("/{id}/cancel") LeaveResponse cancel(Authentication a,@PathVariable Long id){return service.cancel(a.getName(),id);}
  @GetMapping("/balances/me") java.util.List<com.acme.ems.dto.BalanceDtos.BalanceResponse> balances(Authentication a){return balances.balances(employees.current(a.getName()));}
}

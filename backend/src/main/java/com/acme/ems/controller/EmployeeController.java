package com.acme.ems.controller;
import com.acme.ems.dto.EmployeeDtos.*;import com.acme.ems.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/employees")
public class EmployeeController{
  private final EmployeeService service;public EmployeeController(EmployeeService service){this.service=service;}
  @GetMapping("/me") EmployeeResponse me(Authentication a){return EmployeeMapper.toResponse(service.current(a.getName()));}
  @GetMapping("/me/manager") EmployeeResponse manager(Authentication a){var m=service.current(a.getName()).getManager();return m==null?null:EmployeeMapper.toResponse(m);}
  @GetMapping("/me/direct-reports") List<EmployeeResponse> reports(Authentication a){return service.reports(service.current(a.getName()).getId());}
  @GetMapping @PreAuthorize("hasRole('ADMIN')") List<EmployeeResponse> all(){return service.all();}
  @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") EmployeeResponse one(@PathVariable Long id){return service.one(id);}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") EmployeeResponse create(Authentication a,@Valid @RequestBody EmployeeRequest r){return service.create(r,a.getName());}
  @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") EmployeeResponse update(Authentication a,@PathVariable Long id,@Valid @RequestBody EmployeeRequest r){return service.update(id,r,a.getName());}
  @PatchMapping("/{id}/status") @PreAuthorize("hasRole('ADMIN')") EmployeeResponse status(Authentication a,@PathVariable Long id,@Valid @RequestBody StatusRequest r){return service.status(id,Boolean.TRUE.equals(r.active()),a.getName());}
}

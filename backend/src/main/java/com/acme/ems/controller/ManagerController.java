package com.acme.ems.controller;
import com.acme.ems.dto.LeaveDtos.*;import com.acme.ems.model.LeaveStatus;import com.acme.ems.service.LeaveService;
import jakarta.validation.Valid;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/manager/leave-requests") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerController{
  private final LeaveService service;public ManagerController(LeaveService s){service=s;}
  @GetMapping List<LeaveResponse> requests(Authentication a){return service.forManager(a.getName());}
  @PatchMapping("/{id}/approve") LeaveResponse approve(Authentication a,@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return service.review(a.getName(),id,LeaveStatus.APPROVED,r==null?null:r.comment());}
  @PatchMapping("/{id}/reject") LeaveResponse reject(Authentication a,@PathVariable Long id,@Valid @RequestBody RejectionRequest r){return service.review(a.getName(),id,LeaveStatus.REJECTED,r.comment());}
  @GetMapping("/{id}/conflicts") ConflictResponse conflicts(Authentication a,@PathVariable Long id){return service.conflicts(a.getName(),id);}
  @GetMapping("/calendar") List<CalendarEvent> calendar(Authentication a,@RequestParam LocalDate start,@RequestParam LocalDate end){return service.calendar(a.getName(),start,end);}
}

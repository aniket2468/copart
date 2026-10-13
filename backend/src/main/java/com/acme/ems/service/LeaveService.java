package com.acme.ems.service;
import com.acme.ems.dto.LeaveDtos.*;
import com.acme.ems.exception.ApiException;
import com.acme.ems.model.*;
import com.acme.ems.repository.LeaveRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @Transactional
public class LeaveService {
  private final LeaveRequestRepository repo; private final EmployeeService employees; private final LeaveBalanceService balances; private final AuditService audit;
  public LeaveService(LeaveRequestRepository repo,EmployeeService employees,LeaveBalanceService balances,AuditService audit){this.repo=repo;this.employees=employees;this.balances=balances;this.audit=audit;}
  public LeaveResponse create(String email,LeaveCreateRequest r){
    if(r.startDate().isAfter(r.endDate()))throw new ApiException(HttpStatus.BAD_REQUEST,"Start date cannot be after end date");
    Employee e=employees.current(email);int days=workingDays(r.startDate(),r.endDate());if(days<1)throw new ApiException(HttpStatus.BAD_REQUEST,"Leave must include at least one working day");balances.validateAvailable(e,r.leaveType(),days);LeaveRequest l=new LeaveRequest();l.setEmployee(e);l.setLeaveType(r.leaveType());l.setStartDate(r.startDate());l.setEndDate(r.endDate());l.setReason(r.reason().trim());l=repo.save(l);audit.record(e,AuditAction.LEAVE_REQUEST_CREATED,"LeaveRequest",l.getId(),e.fullName()+" requested "+r.leaveType()+" leave from "+r.startDate()+" to "+r.endDate()+" ("+days+" working days)");return map(l);
  }
  @Transactional(readOnly=true) public List<LeaveResponse> mine(String email){return repo.findByEmployeeIdOrderByRequestedAtDesc(employees.current(email).getId()).stream().map(this::map).toList();}
  @Transactional(readOnly=true) public LeaveResponse one(String email,Long id){Employee actor=employees.current(email);LeaveRequest l=get(id);if(actor.getRole()!=Role.ADMIN&&!actor.getId().equals(l.getEmployee().getId())&&!isManager(actor,l))throw forbidden();return map(l);}
  public LeaveResponse cancel(String email,Long id){Employee actor=employees.current(email);LeaveRequest l=get(id);if(!actor.getId().equals(l.getEmployee().getId()))throw forbidden();ensurePending(l);l.setStatus(LeaveStatus.CANCELLED);l=repo.save(l);audit.record(actor,AuditAction.LEAVE_REQUEST_CANCELLED,"LeaveRequest",l.getId(),actor.fullName()+" cancelled "+l.getLeaveType()+" leave request #"+l.getId());return map(l);}
  @Transactional(readOnly=true) public List<LeaveResponse> forManager(String email){Employee a=employees.current(email);List<LeaveRequest> rows=a.getRole()==Role.ADMIN?repo.findAllByOrderByRequestedAtDesc():repo.findByEmployeeManagerIdOrderByRequestedAtDesc(a.getId());return rows.stream().map(this::map).toList();}
  public LeaveResponse review(String email,Long id,LeaveStatus decision,String comment){
    Employee actor=employees.current(email);LeaveRequest l=get(id);if(actor.getId().equals(l.getEmployee().getId()))throw new ApiException(HttpStatus.FORBIDDEN,"Employees cannot review their own leave");
    if(actor.getRole()!=Role.ADMIN&&!isManager(actor,l))throw forbidden();ensurePending(l);if(decision==LeaveStatus.APPROVED)balances.deduct(l.getEmployee(),l.getLeaveType(),workingDays(l.getStartDate(),l.getEndDate()),actor,l.getId());l.setStatus(decision);l.setManagerComment(comment==null?null:comment.trim());l.setReviewedAt(Instant.now());l.setReviewedBy(actor);l=repo.save(l);AuditAction action=decision==LeaveStatus.APPROVED?AuditAction.LEAVE_REQUEST_APPROVED:AuditAction.LEAVE_REQUEST_REJECTED;audit.record(actor,action,"LeaveRequest",l.getId(),actor.fullName()+" "+decision.name().toLowerCase()+" "+l.getEmployee().fullName()+"'s "+l.getLeaveType()+" leave request");return map(l);
  }
  @Transactional(readOnly=true) public List<CalendarEvent> calendar(String email,LocalDate start,LocalDate end){Employee actor=employees.current(email);List<LeaveStatus> statuses=List.of(LeaveStatus.PENDING,LeaveStatus.APPROVED);List<LeaveRequest> rows=actor.getRole()==Role.ADMIN?repo.findOrganizationCalendar(statuses,start,end):repo.findTeamCalendar(actor.getId(),statuses,start,end);return rows.stream().map(l->new CalendarEvent(l.getId(),l.getEmployee().getId(),l.getEmployee().fullName(),l.getLeaveType(),l.getStartDate(),l.getEndDate(),l.getStatus())).toList();}
  @Transactional(readOnly=true) public ConflictResponse conflicts(String email,Long id){Employee actor=employees.current(email);LeaveRequest request=get(id);if(actor.getRole()!=Role.ADMIN&&!isManager(actor,request))throw forbidden();Long managerId=request.getEmployee().getManager()==null?actor.getId():request.getEmployee().getManager().getId();List<LeaveRequest> overlaps=repo.findApprovedConflicts(managerId,request.getEmployee().getId(),request.getId(),request.getStartDate(),request.getEndDate());Map<LocalDate,List<String>> days=new TreeMap<>();for(LeaveRequest l:overlaps){LocalDate from=l.getStartDate().isAfter(request.getStartDate())?l.getStartDate():request.getStartDate();LocalDate to=l.getEndDate().isBefore(request.getEndDate())?l.getEndDate():request.getEndDate();for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))if(d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY)days.computeIfAbsent(d,k->new ArrayList<>()).add(l.getEmployee().fullName());}return new ConflictResponse((int)overlaps.stream().map(x->x.getEmployee().getId()).distinct().count(),days.entrySet().stream().map(x->new ConflictDay(x.getKey(),x.getValue())).toList());}
  private boolean isManager(Employee a,LeaveRequest l){return l.getEmployee().getManager()!=null&&a.getId().equals(l.getEmployee().getManager().getId());}
  private void ensurePending(LeaveRequest l){if(l.getStatus()!=LeaveStatus.PENDING)throw new ApiException(HttpStatus.CONFLICT,"Only pending leave requests can be changed");}
  private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"You are not authorized to access this leave request");}
  private LeaveRequest get(Long id){return repo.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Leave request not found"));}
  public static int workingDays(LocalDate start,LocalDate end){int count=0;for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1))if(d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY)count++;return count;}
  private LeaveResponse map(LeaveRequest l){Employee e=l.getEmployee();return new LeaveResponse(l.getId(),new EmployeeSummary(e.getId(),e.getEmployeeId(),e.fullName(),e.getJobTitle(),e.getDepartment()),l.getLeaveType(),l.getStartDate(),l.getEndDate(),workingDays(l.getStartDate(),l.getEndDate()),l.getReason(),l.getStatus(),l.getManagerComment(),l.getRequestedAt(),l.getReviewedAt(),l.getReviewedBy()==null?null:l.getReviewedBy().fullName());}
}

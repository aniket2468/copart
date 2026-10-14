package com.acme.ems.service;
import com.acme.ems.dto.AdminDtos.*;import com.acme.ems.dto.BalanceDtos.*;import com.acme.ems.dto.EmployeeDtos.*;import com.acme.ems.model.*;import com.acme.ems.repository.*;
import org.springframework.data.domain.*;import org.springframework.data.jpa.domain.Specification;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;
import com.acme.ems.exception.ApiException;import org.springframework.http.HttpStatus;

@Service @Transactional
public class AdminService{
  private final EmployeeRepository employeesRepo;private final LeaveRequestRepository leaves;private final AuditLogRepository audits;private final EmployeeService employees;private final LeaveBalanceService balances;private final Clock clock;
  public AdminService(EmployeeRepository er,LeaveRequestRepository lr,AuditLogRepository ar,EmployeeService es,LeaveBalanceService bs,Clock clock){employeesRepo=er;leaves=lr;audits=ar;employees=es;balances=bs;this.clock=clock;}
  @Transactional(readOnly=true) public DashboardResponse dashboard(){return new DashboardResponse(employeesRepo.count(),employeesRepo.countByActiveTrue(),employeesRepo.countByRole(Role.MANAGER),leaves.countByStatus(LeaveStatus.PENDING),leaves.countEmployeesOnLeave(LocalDate.now(clock)));}
  public List<BalanceResponse> balances(Long id){return balances.balances(employees.byId(id));}
  public BalanceResponse adjust(Long id,LeaveType type,BalanceAdjustmentRequest r,String actorEmail){return balances.adjust(employees.byId(id),type,r.totalAllocated(),r.reason(),employees.current(actorEmail));}
  @Transactional(readOnly=true) public Page<AuditResponse> auditLogs(Long actorId,AuditAction action,String entityType,Instant from,Instant to,Pageable pageable){if(from!=null&&to!=null&&from.isAfter(to))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AUDIT_RANGE","Audit start must not be after audit end");Specification<AuditLog> spec=Specification.unrestricted();if(actorId!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("actor").get("id"),actorId));if(action!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("action"),action));if(entityType!=null&&!entityType.isBlank())spec=spec.and((root,q,cb)->cb.equal(root.get("entityType"),entityType));if(from!=null)spec=spec.and((root,q,cb)->cb.greaterThanOrEqualTo(root.get("timestamp"),from));if(to!=null)spec=spec.and((root,q,cb)->cb.lessThanOrEqualTo(root.get("timestamp"),to));return audits.findAll(spec,pageable).map(this::map);}
  private AuditResponse map(AuditLog a){return new AuditResponse(a.getId(),a.getActor()==null?null:a.getActor().getId(),a.getActor()==null?"System":a.getActor().fullName(),a.getAction(),a.getEntityType(),a.getEntityId(),a.getDescription(),a.getTimestamp());}
}


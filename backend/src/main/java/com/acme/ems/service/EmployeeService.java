package com.acme.ems.service;
import com.acme.ems.dto.EmployeeDtos.*;
import com.acme.ems.exception.ApiException;
import com.acme.ems.model.*;
import com.acme.ems.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional
public class EmployeeService {
  private final EmployeeRepository repo; private final PasswordEncoder encoder; private final AuditService audit; private final LeaveBalanceService balances;
  public EmployeeService(EmployeeRepository repo,PasswordEncoder encoder,AuditService audit,LeaveBalanceService balances){this.repo=repo;this.encoder=encoder;this.audit=audit;this.balances=balances;}
  @Transactional(readOnly=true) public Employee current(String email){return repo.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Account not found"));}
  public void lockForLeaveSubmission(Employee employee){repo.findByIdForUpdate(employee.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Employee not found"));}
  @Transactional(readOnly=true) public List<EmployeeResponse> all(){return repo.findAll().stream().map(EmployeeMapper::toResponse).toList();}
  @Transactional(readOnly=true) public EmployeeResponse one(Long id){return EmployeeMapper.toResponse(get(id));}
  @Transactional(readOnly=true) public List<EmployeeResponse> reports(Long managerId){return repo.findByManagerIdOrderByFirstName(managerId).stream().map(EmployeeMapper::toResponse).toList();}
  public EmployeeResponse create(EmployeeRequest r,String actorEmail){
    unique(normalizeEmail(r.email()),normalize(r.employeeId()),null); Employee e=new Employee(); apply(e,r,true);e=repo.save(e);balances.initialize(e);Employee actor=current(actorEmail);audit.record(actor,AuditAction.EMPLOYEE_CREATED,"Employee",e.getId(),actor.fullName()+" created employee "+e.fullName()+" ("+e.getEmployeeId()+")");return EmployeeMapper.toResponse(e);
  }
  public EmployeeResponse update(Long id,EmployeeRequest r,String actorEmail){Employee e=get(id);Employee actor=current(actorEmail);Role oldRole=e.getRole();Employee oldManager=e.getManager();unique(normalizeEmail(r.email()),normalize(r.employeeId()),id);if(oldRole!=r.role())validateRoleChange(e,r.role());apply(e,r,false);e=repo.save(e);audit.record(actor,AuditAction.EMPLOYEE_UPDATED,"Employee",e.getId(),actor.fullName()+" updated "+e.fullName()+"'s employee record");if(oldRole!=e.getRole())audit.record(actor,AuditAction.ROLE_CHANGED,"Employee",e.getId(),"Changed "+e.fullName()+"'s role from "+oldRole+" to "+e.getRole());Long oldId=oldManager==null?null:oldManager.getId(),newId=e.getManager()==null?null:e.getManager().getId();if(!Objects.equals(oldId,newId))audit.record(actor,AuditAction.MANAGER_CHANGED,"Employee",e.getId(),"Changed "+e.fullName()+"'s manager from "+(oldManager==null?"none":oldManager.fullName())+" to "+(e.getManager()==null?"none":e.getManager().fullName()));return EmployeeMapper.toResponse(e);}
  public EmployeeResponse status(Long id,boolean active,String actorEmail){Employee e=get(id);Employee actor=current(actorEmail);if(!active&&e.isActive()){if(repo.existsByManagerId(id))throw new ApiException(HttpStatus.CONFLICT,"MANAGER_HAS_DIRECT_REPORTS","Reassign direct reports before deactivating this manager");if(e.getRole()==Role.ADMIN)ensureAnotherActiveAdmin(e);}e.setActive(active);e=repo.save(e);audit.record(actor,active?AuditAction.EMPLOYEE_REACTIVATED:AuditAction.EMPLOYEE_DEACTIVATED,"Employee",e.getId(),actor.fullName()+" "+(active?"reactivated ":"deactivated ")+e.fullName());return EmployeeMapper.toResponse(e);}
  public EmployeeResponse changeManager(Long id,Long managerId,String actorEmail){Employee e=get(id);Employee old=e.getManager();Employee manager=managerId==null?null:get(managerId);if(manager!=null&&manager.getId().equals(id))throw new ApiException(HttpStatus.BAD_REQUEST,"An employee cannot be their own manager");if(manager!=null)validateManagerCandidate(manager);if(manager!=null&&createsCycle(e,manager))throw new ApiException(HttpStatus.BAD_REQUEST,"Manager assignment would create a reporting cycle");e.setManager(manager);e=repo.save(e);Employee actor=current(actorEmail);audit.record(actor,AuditAction.MANAGER_CHANGED,"Employee",id,"Changed "+e.fullName()+"'s manager from "+(old==null?"none":old.fullName())+" to "+(manager==null?"none":manager.fullName()));return EmployeeMapper.toResponse(e);}
  public EmployeeResponse changeRole(Long id,Role role,String actorEmail){Employee e=get(id);Role old=e.getRole();validateRoleChange(e,role);e.setRole(role);e=repo.save(e);Employee actor=current(actorEmail);audit.record(actor,AuditAction.ROLE_CHANGED,"Employee",id,"Changed "+e.fullName()+"'s role from "+old+" to "+role);return EmployeeMapper.toResponse(e);}
  @Transactional(readOnly=true) public Employee byId(Long id){return get(id);}
  private void apply(Employee e,EmployeeRequest r,boolean creating){
    e.setEmployeeId(normalize(r.employeeId()));e.setFirstName(normalize(r.firstName()));e.setLastName(normalize(r.lastName()));e.setEmail(normalizeEmail(r.email()));
    e.setJobTitle(r.jobTitle().trim());e.setDepartment(r.department().trim());e.setRole(r.role());e.setDateOfJoining(r.dateOfJoining());
    if(creating && (r.password()==null||r.password().isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST,"Password is required");
    if(r.password()!=null&&!r.password().isBlank()) e.setPasswordHash(encoder.encode(r.password()));
    Employee manager=r.managerId()==null?null:get(r.managerId());
    if(e.getId()!=null && manager!=null && e.getId().equals(manager.getId())) throw new ApiException(HttpStatus.BAD_REQUEST,"An employee cannot be their own manager");
    if(manager!=null)validateManagerCandidate(manager);
    if(manager!=null && createsCycle(e,manager)) throw new ApiException(HttpStatus.BAD_REQUEST,"Manager assignment would create a reporting cycle");
    e.setManager(manager);
  }
  private boolean createsCycle(Employee e,Employee manager){for(Employee p=manager;p!=null;p=p.getManager())if(e.getId()!=null&&e.getId().equals(p.getId()))return true;return false;}
  private void unique(String email,String employeeId,Long current){
    boolean emailExists=current==null?repo.existsByEmailIgnoreCase(email):repo.existsByEmailIgnoreCaseAndIdNot(email,current);
    boolean idExists=current==null?repo.existsByEmployeeId(employeeId):repo.existsByEmployeeIdAndIdNot(employeeId,current);
    if(emailExists)throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_EMAIL","Email is already in use");
    if(idExists)throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_EMPLOYEE_ID","Employee ID is already in use");
  }
  private void validateManagerCandidate(Employee manager){if(!manager.isActive()||(manager.getRole()!=Role.MANAGER&&manager.getRole()!=Role.ADMIN))throw new ApiException(HttpStatus.CONFLICT,"INVALID_MANAGER","A manager must be an active manager or administrator");}
  private void validateRoleChange(Employee employee,Role role){if(employee.getRole()==role)return;if((employee.getRole()==Role.MANAGER||employee.getRole()==Role.ADMIN)&&role==Role.EMPLOYEE&&repo.existsByManagerId(employee.getId()))throw new ApiException(HttpStatus.CONFLICT,"MANAGER_HAS_DIRECT_REPORTS","Reassign direct reports before changing this manager's role");if(employee.getRole()==Role.ADMIN&&role!=Role.ADMIN&&employee.isActive())ensureAnotherActiveAdmin(employee);}
  private void ensureAnotherActiveAdmin(Employee employee){long others=repo.findActiveAdminsForUpdate().stream().filter(admin->!admin.getId().equals(employee.getId())).count();if(others==0)throw new ApiException(HttpStatus.CONFLICT,"LAST_ACTIVE_ADMIN_REQUIRED","At least one active administrator is required");}
  private String normalize(String value){return value.trim();}
  private String normalizeEmail(String value){return normalize(value).toLowerCase(Locale.ROOT);}
  private Employee get(Long id){return repo.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Employee not found"));}
}

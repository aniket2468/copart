package com.acme.ems.repository;
import com.acme.ems.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long>{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from LeaveRequest l join fetch l.employee e left join fetch e.manager where l.id=:id")
  Optional<LeaveRequest> findByIdForUpdate(Long id);
  List<LeaveRequest> findByEmployeeIdOrderByRequestedAtDesc(Long employeeId);
  List<LeaveRequest> findByEmployeeManagerIdOrderByRequestedAtDesc(Long managerId);
  List<LeaveRequest> findAllByOrderByRequestedAtDesc();
  long countByStatus(LeaveStatus status);
  @Query("select count(distinct l.employee.id) from LeaveRequest l where l.status='APPROVED' and l.startDate<=:today and l.endDate>=:today")
  long countEmployeesOnLeave(LocalDate today);
  @Query("select l from LeaveRequest l join fetch l.employee e where e.manager.id=:managerId and l.status in :statuses and l.startDate<=:end and l.endDate>=:start order by l.startDate")
  List<LeaveRequest> findTeamCalendar(Long managerId,Collection<LeaveStatus> statuses,LocalDate start,LocalDate end);
  @Query("select l from LeaveRequest l join fetch l.employee e where l.status in :statuses and l.startDate<=:end and l.endDate>=:start order by l.startDate")
  List<LeaveRequest> findOrganizationCalendar(Collection<LeaveStatus> statuses,LocalDate start,LocalDate end);
  @Query("select l from LeaveRequest l join fetch l.employee e where e.manager.id=:managerId and l.status='APPROVED' and l.id<>:excludeId and e.id<>:employeeId and l.startDate<=:end and l.endDate>=:start order by l.startDate")
  List<LeaveRequest> findApprovedConflicts(Long managerId,Long employeeId,Long excludeId,LocalDate start,LocalDate end);
  @Query("select count(l)>0 from LeaveRequest l where l.employee.id=:employeeId and l.status in ('PENDING','APPROVED') and l.startDate<=:end and l.endDate>=:start")
  boolean existsActiveOverlap(Long employeeId,LocalDate start,LocalDate end);
}

package com.acme.ems.repository;
import com.acme.ems.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long>{
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
}

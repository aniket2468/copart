package com.acme.ems.repository;
import com.acme.ems.model.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance,Long>{
  List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveType(Long employeeId,int year);
  Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeAndYear(Long employeeId,LeaveType type,int year);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from LeaveBalance b where b.employee.id=:employeeId and b.leaveType=:type and b.year=:year")
  Optional<LeaveBalance> findForUpdate(Long employeeId,LeaveType type,int year);
}


package com.acme.ems.repository;
import com.acme.ems.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface EmployeeRepository extends JpaRepository<Employee,Long>{
  @EntityGraph(attributePaths="manager") Optional<Employee> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email); boolean existsByEmployeeId(String employeeId);
  boolean existsByEmailIgnoreCaseAndIdNot(String email,Long id);
  boolean existsByEmployeeIdAndIdNot(String employeeId,Long id);
  boolean existsByManagerId(Long managerId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Employee e where e.id=:id")
  Optional<Employee> findByIdForUpdate(Long id);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Employee e where e.role='ADMIN' and e.active=true order by e.id")
  List<Employee> findActiveAdminsForUpdate();
  List<Employee> findByManagerIdOrderByFirstName(Long managerId);
  long countByActiveTrue();
  long countByRole(com.acme.ems.model.Role role);
}

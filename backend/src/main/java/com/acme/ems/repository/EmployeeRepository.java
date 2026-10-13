package com.acme.ems.repository;
import com.acme.ems.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface EmployeeRepository extends JpaRepository<Employee,Long>{
  @EntityGraph(attributePaths="manager") Optional<Employee> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email); boolean existsByEmployeeId(String employeeId);
  List<Employee> findByManagerIdOrderByFirstName(Long managerId);
  long countByActiveTrue();
  long countByRole(com.acme.ems.model.Role role);
}

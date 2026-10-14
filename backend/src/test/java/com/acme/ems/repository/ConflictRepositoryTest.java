package com.acme.ems.repository;
import com.acme.ems.model.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;import java.time.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class ConflictRepositoryTest{
 @Autowired EmployeeRepository employees;@Autowired LeaveRequestRepository leaves;Employee manager,requester,teammate,outsider;
 @BeforeEach void setup(){manager=employees.save(employee("M1","Manager",null));requester=employees.save(employee("E1","John",manager));teammate=employees.save(employee("E2","Emma",manager));Employee otherManager=employees.save(employee("M2","OtherManager",null));outsider=employees.save(employee("E3","David",otherManager));}
 @Test void overlappingApprovedLeaveIsDetected(){LeaveRequest existing=leave(teammate,LeaveStatus.APPROVED,LocalDate.of(2026,11,11),LocalDate.of(2026,11,12));assertEquals(List.of(existing.getId()),query().stream().map(LeaveRequest::getId).toList());}
 @Test void nonOverlappingLeaveIsIgnored(){leave(teammate,LeaveStatus.APPROVED,LocalDate.of(2026,11,13),LocalDate.of(2026,11,14));assertTrue(query().isEmpty());}
 @Test void rejectedAndCancelledLeaveAreIgnored(){leave(teammate,LeaveStatus.REJECTED,LocalDate.of(2026,11,11),LocalDate.of(2026,11,12));leave(teammate,LeaveStatus.CANCELLED,LocalDate.of(2026,11,10),LocalDate.of(2026,11,11));assertTrue(query().isEmpty());}
 @Test void unrelatedTeamIsIgnored(){leave(outsider,LeaveStatus.APPROVED,LocalDate.of(2026,11,10),LocalDate.of(2026,11,12));assertTrue(query().isEmpty());}
 @Test void multipleOverlapsAreReturned(){leave(teammate,LeaveStatus.APPROVED,LocalDate.of(2026,11,10),LocalDate.of(2026,11,10));Employee second=employees.save(employee("E4","Maya",manager));leave(second,LeaveStatus.APPROVED,LocalDate.of(2026,11,12),LocalDate.of(2026,11,12));assertEquals(2,query().size());}
 private List<LeaveRequest> query(){return leaves.findApprovedConflicts(manager.getId(),requester.getId(),999L,LocalDate.of(2026,11,10),LocalDate.of(2026,11,12));}
 private LeaveRequest leave(Employee e,LeaveStatus s,LocalDate start,LocalDate end){LeaveRequest l=new LeaveRequest();l.setEmployee(e);l.setLeaveType(LeaveType.VACATION);l.setStartDate(start);l.setEndDate(end);l.setReason("Test");l.setStatus(s);return leaves.saveAndFlush(l);}
 private Employee employee(String id,String first,Employee manager){Employee e=new Employee();e.setEmployeeId(id);e.setFirstName(first);e.setLastName("User");e.setEmail(id.toLowerCase()+"@x.com");e.setPasswordHash("hash");e.setJobTitle("Engineer");e.setDepartment("Tech");e.setRole(manager==null?Role.MANAGER:Role.EMPLOYEE);e.setManager(manager);e.setDateOfJoining(LocalDate.of(2024,1,1));e.setActive(true);return e;}
}

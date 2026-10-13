package com.acme.ems.service;

import com.acme.ems.dto.LeaveDtos.*;
import com.acme.ems.exception.ApiException;
import com.acme.ems.model.*;
import com.acme.ems.repository.LeaveRequestRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {
  @Mock LeaveRequestRepository repo; @Mock EmployeeService employees; @Mock LeaveBalanceService balances; @Mock AuditService audit; LeaveService service;
  Employee manager,employee,outsider; LeaveRequest pending;
  @BeforeEach void setup(){service=new LeaveService(repo,employees,balances,audit);manager=user(1L,"manager@x.com",Role.MANAGER);employee=user(2L,"employee@x.com",Role.EMPLOYEE);employee.setManager(manager);outsider=user(3L,"other@x.com",Role.EMPLOYEE);pending=new LeaveRequest();pending.setId(10L);pending.setEmployee(employee);pending.setLeaveType(LeaveType.VACATION);pending.setStartDate(LocalDate.of(2026,11,10));pending.setEndDate(LocalDate.of(2026,11,12));pending.setReason("Trip");pending.setStatus(LeaveStatus.PENDING);lenient().when(repo.save(any())).thenAnswer(i->i.getArgument(0));}
  @Test void employeeSubmitsLeave(){when(employees.current(employee.getEmail())).thenReturn(employee);var result=service.create(employee.getEmail(),new LeaveCreateRequest(LeaveType.VACATION,pending.getStartDate(),pending.getEndDate(),"Trip"));assertEquals(LeaveStatus.PENDING,result.status());assertEquals(3,result.days());verify(repo).save(any());}
  @Test void invalidDatesAreRejected(){var ex=assertThrows(ApiException.class,()->service.create(employee.getEmail(),new LeaveCreateRequest(LeaveType.SICK,LocalDate.of(2026,3,2),LocalDate.of(2026,3,1),"Sick")));assertEquals(HttpStatus.BAD_REQUEST,ex.status());}
  @Test void actualManagerApprovesAndDeductsBalance(){mockLookup(manager);var result=service.review(manager.getEmail(),10L,LeaveStatus.APPROVED,null);assertEquals(LeaveStatus.APPROVED,result.status());assertEquals(manager,pending.getReviewedBy());verify(balances).deduct(employee,LeaveType.VACATION,3,manager,10L);verify(audit).record(eq(manager),eq(AuditAction.LEAVE_REQUEST_APPROVED),eq("LeaveRequest"),eq(10L),anyString());}
  @Test void actualManagerRejectsWithCommentWithoutDeduction(){mockLookup(manager);var result=service.review(manager.getEmail(),10L,LeaveStatus.REJECTED,"Coverage unavailable");assertEquals(LeaveStatus.REJECTED,result.status());assertEquals("Coverage unavailable",result.managerComment());verifyNoInteractions(balances);verify(audit).record(eq(manager),eq(AuditAction.LEAVE_REQUEST_REJECTED),eq("LeaveRequest"),eq(10L),anyString());}
  @Test void unauthorizedEmployeeCannotApprove(){mockLookup(outsider);var ex=assertThrows(ApiException.class,()->service.review(outsider.getEmail(),10L,LeaveStatus.APPROVED,null));assertEquals(HttpStatus.FORBIDDEN,ex.status());}
  @Test void employeeCannotApproveOwnLeave(){mockLookup(employee);var ex=assertThrows(ApiException.class,()->service.review(employee.getEmail(),10L,LeaveStatus.APPROVED,null));assertEquals(HttpStatus.FORBIDDEN,ex.status());}
  @Test void employeeCancelsPendingLeave(){mockLookup(employee);assertEquals(LeaveStatus.CANCELLED,service.cancel(employee.getEmail(),10L).status());}
  @Test void decidedLeaveCannotBeCancelled(){pending.setStatus(LeaveStatus.APPROVED);mockLookup(employee);var ex=assertThrows(ApiException.class,()->service.cancel(employee.getEmail(),10L));assertEquals(HttpStatus.CONFLICT,ex.status());}
  @Test void weekendDaysAreExcluded(){assertEquals(3,LeaveService.workingDays(LocalDate.of(2026,11,6),LocalDate.of(2026,11,10)));}
  @Test void overlappingApprovedLeaveIsReportedByDay(){Employee emma=user(4L,"emma@x.com",Role.EMPLOYEE);emma.setFirstName("Emma");emma.setManager(manager);LeaveRequest other=new LeaveRequest();other.setId(11L);other.setEmployee(emma);other.setLeaveType(LeaveType.PERSONAL);other.setStartDate(LocalDate.of(2026,11,11));other.setEndDate(LocalDate.of(2026,11,12));other.setStatus(LeaveStatus.APPROVED);mockLookup(manager);when(repo.findApprovedConflicts(1L,2L,10L,pending.getStartDate(),pending.getEndDate())).thenReturn(List.of(other));var result=service.conflicts(manager.getEmail(),10L);assertEquals(1,result.conflictingEmployees());assertEquals(2,result.days().size());}
  @Test void noApprovedOverlapReturnsNoConflict(){mockLookup(manager);when(repo.findApprovedConflicts(anyLong(),anyLong(),anyLong(),any(),any())).thenReturn(List.of());assertEquals(0,service.conflicts(manager.getEmail(),10L).conflictingEmployees());}
  @Test void adminCalendarShowsOrganizationLeave(){Employee admin=user(5L,"admin@x.com",Role.ADMIN);LocalDate start=LocalDate.of(2026,10,1),end=LocalDate.of(2026,10,31);when(employees.current(admin.getEmail())).thenReturn(admin);when(repo.findOrganizationCalendar(anyCollection(),eq(start),eq(end))).thenReturn(List.of(pending));var result=service.calendar(admin.getEmail(),start,end);assertEquals(1,result.size());verify(repo).findOrganizationCalendar(anyCollection(),eq(start),eq(end));verify(repo,never()).findTeamCalendar(anyLong(),anyCollection(),any(),any());}
  @Test void managerCalendarRemainsLimitedToDirectReports(){LocalDate start=LocalDate.of(2026,10,1),end=LocalDate.of(2026,10,31);when(employees.current(manager.getEmail())).thenReturn(manager);when(repo.findTeamCalendar(eq(manager.getId()),anyCollection(),eq(start),eq(end))).thenReturn(List.of(pending));assertEquals(1,service.calendar(manager.getEmail(),start,end).size());verify(repo,never()).findOrganizationCalendar(anyCollection(),any(),any());}
  private void mockLookup(Employee actor){when(employees.current(actor.getEmail())).thenReturn(actor);when(repo.findById(10L)).thenReturn(Optional.of(pending));}
  private Employee user(Long id,String email,Role role){Employee e=new Employee();e.setId(id);e.setEmployeeId("E"+id);e.setFirstName("User");e.setLastName(String.valueOf(id));e.setEmail(email);e.setRole(role);e.setJobTitle("Engineer");e.setDepartment("Tech");e.setDateOfJoining(LocalDate.of(2024,1,1));return e;}
}

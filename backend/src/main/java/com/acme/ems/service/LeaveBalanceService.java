package com.acme.ems.service;

import com.acme.ems.dto.BalanceDtos.BalanceResponse;
import com.acme.ems.exception.ApiException;
import com.acme.ems.model.*;
import com.acme.ems.repository.LeaveBalanceRepository;
import com.acme.ems.repository.EmployeeRepository;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeaveBalanceService {
  private static final int MAXIMUM_ANNUAL_ALLOCATION = 366;
  private final LeaveBalanceRepository repo;
  private final AuditService audit;
  private final Clock clock;
  private final EmployeeRepository employees;

  public LeaveBalanceService(LeaveBalanceRepository repo, AuditService audit, Clock clock,
      EmployeeRepository employees) {
    this.repo = repo;
    this.audit = audit;
    this.clock = clock;
    this.employees = employees;
  }

  public void initialize(Employee employee) {
    initialize(employee, Year.now(clock).getValue());
  }

  public void initialize(Employee employee, int year) {
    if (repo.findByEmployeeIdAndYearOrderByLeaveType(employee.getId(), year).size() == 3) return;
    // Serialize creation on an existing parent row, including the first request of a new year.
    employees.findByIdForUpdate(employee.getId()).orElseThrow();
    createIfMissing(employee, LeaveType.VACATION, 15, year);
    createIfMissing(employee, LeaveType.SICK, 10, year);
    createIfMissing(employee, LeaveType.PERSONAL, 5, year);
  }

  public List<BalanceResponse> balances(Employee employee) {
    int year = Year.now(clock).getValue();
    initialize(employee, year);
    return repo.findByEmployeeIdAndYearOrderByLeaveType(employee.getId(), year).stream()
        .map(this::map).toList();
  }

  public void validateAvailable(Employee employee, LeaveType type, int days, int year) {
    if (!tracked(type)) return;
    LeaveBalance balance = getOrCreate(employee, type, year);
    if (days > balance.remaining()) {
      throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_LEAVE_BALANCE",
          "Insufficient " + type.name().toLowerCase() + " leave balance: "
              + balance.remaining() + " working days remaining");
    }
  }

  public void deduct(Employee employee, LeaveType type, int days, int year,
      Employee actor, Long requestId) {
    if (!tracked(type)) return;
    LeaveBalance balance = getForUpdate(employee, type, year);
    if (days > balance.remaining()) {
      throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_LEAVE_BALANCE",
          "Insufficient leave balance at approval time");
    }
    balance.setUsed(balance.getUsed() + days);
    repo.save(balance);
    audit.record(actor, AuditAction.LEAVE_BALANCE_UPDATED, "LeaveBalance", balance.getId(),
        "Deducted " + days + " working days for approved leave request #" + requestId
            + "; " + balance.remaining() + " days remain");
  }

  public BalanceResponse adjust(Employee employee, LeaveType type, int allocated,
      String reason, Employee actor) {
    if (!tracked(type)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UNTRACKED_LEAVE_TYPE",
          "Only Vacation, Sick, and Personal balances are tracked");
    }
    if (allocated < 0 || allocated > MAXIMUM_ANNUAL_ALLOCATION) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEAVE_ALLOCATION",
          "Annual leave allocation must be between 0 and " + MAXIMUM_ANNUAL_ALLOCATION);
    }
    LeaveBalance balance = getForUpdate(employee, type, Year.now(clock).getValue());
    if (allocated < balance.getUsed()) {
      throw new ApiException(HttpStatus.CONFLICT, "ALLOCATION_BELOW_USED",
          "Allocation cannot be lower than used leave (" + balance.getUsed() + ")");
    }
    int old = balance.getTotalAllocated();
    balance.setTotalAllocated(allocated);
    repo.save(balance);
    audit.record(actor, AuditAction.LEAVE_BALANCE_UPDATED, "LeaveBalance", balance.getId(),
        "Changed " + employee.fullName() + "'s " + type + " allocation from " + old
            + " to " + allocated + " days. Reason: " + reason.trim());
    return map(balance);
  }

  private LeaveBalance getOrCreate(Employee employee, LeaveType type, int year) {
    initialize(employee, year);
    return repo.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), type, year).orElseThrow();
  }

  private LeaveBalance getForUpdate(Employee employee, LeaveType type, int year) {
    getOrCreate(employee, type, year);
    return repo.findForUpdate(employee.getId(), type, year).orElseThrow();
  }

  private void createIfMissing(Employee employee, LeaveType type, int amount, int year) {
    if (repo.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), type, year).isEmpty()) {
      LeaveBalance balance = new LeaveBalance();
      balance.setEmployee(employee);
      balance.setLeaveType(type);
      balance.setTotalAllocated(amount);
      balance.setUsed(0);
      balance.setYear(year);
      repo.save(balance);
    }
  }

  private boolean tracked(LeaveType type) {
    return type == LeaveType.VACATION || type == LeaveType.SICK || type == LeaveType.PERSONAL;
  }

  private BalanceResponse map(LeaveBalance balance) {
    return new BalanceResponse(balance.getId(), balance.getLeaveType(), balance.getTotalAllocated(),
        balance.getUsed(), balance.remaining(), balance.getYear());
  }
}

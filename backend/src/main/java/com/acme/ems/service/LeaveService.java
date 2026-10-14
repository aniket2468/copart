package com.acme.ems.service;

import com.acme.ems.dto.LeaveDtos.*;
import com.acme.ems.exception.ApiException;
import com.acme.ems.model.*;
import com.acme.ems.repository.LeaveRequestRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeaveService {
  private static final int MINIMUM_SUPPORTED_YEAR = 1;
  private static final int MAXIMUM_SUPPORTED_YEAR = 9999;
  private final LeaveRequestRepository repo;
  private final EmployeeService employees;
  private final LeaveBalanceService balances;
  private final AuditService audit;
  private final Clock clock;
  private final int maximumRangeDays;

  public LeaveService(LeaveRequestRepository repo, EmployeeService employees,
      LeaveBalanceService balances, AuditService audit, Clock clock,
      @Value("${app.leave.max-range-days:366}") int maximumRangeDays) {
    this.repo = repo;
    this.employees = employees;
    this.balances = balances;
    this.audit = audit;
    this.clock = clock;
    this.maximumRangeDays = maximumRangeDays;
  }

  public LeaveResponse create(String email, LeaveCreateRequest request) {
    Employee employee = employees.current(email);
    validateDates(employee, request.startDate(), request.endDate());
    employees.lockForLeaveSubmission(employee);
    if (repo.existsActiveOverlap(employee.getId(), request.startDate(), request.endDate())) {
      throw new ApiException(HttpStatus.CONFLICT, "OVERLAPPING_LEAVE_REQUEST",
          "The requested dates overlap an existing pending or approved leave request");
    }
    int days = workingDays(request.startDate(), request.endDate());
    balances.validateAvailable(employee, request.leaveType(), days, request.startDate().getYear());
    LeaveRequest leave = new LeaveRequest();
    leave.setEmployee(employee);
    leave.setLeaveType(request.leaveType());
    leave.setStartDate(request.startDate());
    leave.setEndDate(request.endDate());
    leave.setReason(request.reason().trim());
    leave = repo.save(leave);
    audit.record(employee, AuditAction.LEAVE_REQUEST_CREATED, "LeaveRequest", leave.getId(),
        employee.fullName() + " requested " + request.leaveType() + " leave from "
            + request.startDate() + " to " + request.endDate() + " (" + days + " working days)");
    return map(leave);
  }

  @Transactional(readOnly = true)
  public List<LeaveResponse> mine(String email) {
    return repo.findByEmployeeIdOrderByRequestedAtDesc(employees.current(email).getId()).stream()
        .map(this::map).toList();
  }

  @Transactional(readOnly = true)
  public LeaveResponse one(String email, Long id) {
    Employee actor = employees.current(email);
    LeaveRequest leave = get(id);
    if (actor.getRole() != Role.ADMIN && !actor.getId().equals(leave.getEmployee().getId())
        && !isManager(actor, leave)) throw forbidden();
    return map(leave);
  }

  public LeaveResponse cancel(String email, Long id) {
    Employee actor = employees.current(email);
    LeaveRequest leave = getForUpdate(id);
    if (!actor.getId().equals(leave.getEmployee().getId())) throw forbidden();
    ensurePending(leave);
    leave.setStatus(LeaveStatus.CANCELLED);
    leave = repo.save(leave);
    audit.record(actor, AuditAction.LEAVE_REQUEST_CANCELLED, "LeaveRequest", leave.getId(),
        actor.fullName() + " cancelled " + leave.getLeaveType() + " leave request #" + leave.getId());
    return map(leave);
  }

  @Transactional(readOnly = true)
  public List<LeaveResponse> forManager(String email) {
    Employee actor = employees.current(email);
    List<LeaveRequest> rows = actor.getRole() == Role.ADMIN
        ? repo.findAllByOrderByRequestedAtDesc()
        : repo.findByEmployeeManagerIdOrderByRequestedAtDesc(actor.getId());
    return rows.stream().map(this::map).toList();
  }

  public LeaveResponse review(String email, Long id, LeaveStatus decision, String comment) {
    Employee actor = employees.current(email);
    LeaveRequest leave = getForUpdate(id);
    if (actor.getId().equals(leave.getEmployee().getId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN",
          "Employees cannot review their own leave");
    }
    if (actor.getRole() != Role.ADMIN && !isManager(actor, leave)) throw forbidden();
    ensurePending(leave);
    if (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEAVE_DECISION", "Unsupported leave decision");
    }
    if (decision == LeaveStatus.REJECTED && (comment == null || comment.isBlank())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
          "A rejection reason is required");
    }
    if (decision == LeaveStatus.APPROVED) {
      balances.deduct(leave.getEmployee(), leave.getLeaveType(),
          workingDays(leave.getStartDate(), leave.getEndDate()), leave.getStartDate().getYear(),
          actor, leave.getId());
    }
    leave.setStatus(decision);
    leave.setManagerComment(comment == null ? null : comment.trim());
    leave.setReviewedAt(Instant.now(clock));
    leave.setReviewedBy(actor);
    leave = repo.save(leave);
    AuditAction action = decision == LeaveStatus.APPROVED
        ? AuditAction.LEAVE_REQUEST_APPROVED : AuditAction.LEAVE_REQUEST_REJECTED;
    audit.record(actor, action, "LeaveRequest", leave.getId(), actor.fullName() + " "
        + decision.name().toLowerCase() + " " + leave.getEmployee().fullName() + "'s "
        + leave.getLeaveType() + " leave request");
    return map(leave);
  }

  @Transactional(readOnly = true)
  public List<CalendarEvent> calendar(String email, LocalDate start, LocalDate end) {
    validateRange(start, end, "calendar");
    Employee actor = employees.current(email);
    List<LeaveStatus> statuses = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
    List<LeaveRequest> rows = actor.getRole() == Role.ADMIN
        ? repo.findOrganizationCalendar(statuses, start, end)
        : repo.findTeamCalendar(actor.getId(), statuses, start, end);
    return rows.stream().map(leave -> new CalendarEvent(leave.getId(), leave.getEmployee().getId(),
        leave.getEmployee().fullName(), leave.getLeaveType(), leave.getStartDate(),
        leave.getEndDate(), leave.getStatus())).toList();
  }

  @Transactional(readOnly = true)
  public ConflictResponse conflicts(String email, Long id) {
    Employee actor = employees.current(email);
    LeaveRequest request = get(id);
    if (actor.getRole() != Role.ADMIN && !isManager(actor, request)) throw forbidden();
    if (request.getEmployee().getManager() == null) return new ConflictResponse(0, List.of());
    Long managerId = request.getEmployee().getManager().getId();
    List<LeaveRequest> overlaps = repo.findApprovedConflicts(managerId, request.getEmployee().getId(),
        request.getId(), request.getStartDate(), request.getEndDate());
    Map<LocalDate, List<String>> days = new TreeMap<>();
    for (LeaveRequest leave : overlaps) {
      LocalDate from = leave.getStartDate().isAfter(request.getStartDate())
          ? leave.getStartDate() : request.getStartDate();
      LocalDate to = leave.getEndDate().isBefore(request.getEndDate())
          ? leave.getEndDate() : request.getEndDate();
      for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
        if (isWorkingDay(date)) {
          days.computeIfAbsent(date, ignored -> new ArrayList<>()).add(leave.getEmployee().fullName());
        }
      }
    }
    int conflictingEmployees = (int) days.values().stream().flatMap(List::stream).distinct().count();
    return new ConflictResponse(conflictingEmployees, days.entrySet().stream()
        .map(entry -> new ConflictDay(entry.getKey(), entry.getValue())).toList());
  }

  private void validateDates(Employee employee, LocalDate start, LocalDate end) {
    validateRange(start, end, "leave");
    if (start.getYear() != end.getYear()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CROSS_YEAR_LEAVE_NOT_SUPPORTED",
          "A leave request must start and end in the same calendar year");
    }
    if (start.isBefore(LocalDate.now(clock))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_START_IN_PAST", "Leave cannot start in the past");
    }
    if (start.isBefore(employee.getDateOfJoining())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_BEFORE_JOINING_DATE",
          "Leave cannot start before the employee joining date");
    }
    if (workingDays(start, end) < 1) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_WORKING_DAYS",
          "Leave must include at least one working day");
    }
  }

  private void validateRange(LocalDate start, LocalDate end, String rangeName) {
    if (start == null || end == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Start and end dates are required");
    }
    if (start.isAfter(end)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
          "Start date cannot be after end date");
    }
    if (start.getYear() < MINIMUM_SUPPORTED_YEAR || end.getYear() > MAXIMUM_SUPPORTED_YEAR) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_OUT_OF_RANGE",
          "Dates must use years from " + MINIMUM_SUPPORTED_YEAR + " through "
              + MAXIMUM_SUPPORTED_YEAR);
    }
    long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
    if (inclusiveDays > maximumRangeDays) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE",
          "The " + rangeName + " range cannot exceed " + maximumRangeDays + " days");
    }
  }

  private boolean isManager(Employee actor, LeaveRequest leave) {
    return leave.getEmployee().getManager() != null
        && actor.getId().equals(leave.getEmployee().getManager().getId());
  }

  private void ensurePending(LeaveRequest leave) {
    if (leave.getStatus() != LeaveStatus.PENDING) {
      throw new ApiException(HttpStatus.CONFLICT, "LEAVE_ALREADY_DECIDED",
          "Only pending leave requests can be changed");
    }
  }

  private ApiException forbidden() {
    return new ApiException(HttpStatus.FORBIDDEN, "LEAVE_ACCESS_FORBIDDEN",
        "You are not authorized to access this leave request");
  }

  private LeaveRequest get(Long id) {
    return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
        "LEAVE_NOT_FOUND", "Leave request not found"));
  }

  private LeaveRequest getForUpdate(Long id) {
    return repo.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
        "LEAVE_NOT_FOUND", "Leave request not found"));
  }

  public static int workingDays(LocalDate start, LocalDate end) {
    long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
    long fullWeeks = totalDays / 7;
    int count = Math.toIntExact(fullWeeks * 5);
    LocalDate remainderStart = start.plusWeeks(fullWeeks);
    for (int offset = 0; offset < totalDays % 7; offset++) {
      if (isWorkingDay(remainderStart.plusDays(offset))) count++;
    }
    return count;
  }

  private static boolean isWorkingDay(LocalDate date) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY
        && date.getDayOfWeek() != DayOfWeek.SUNDAY;
  }

  private LeaveResponse map(LeaveRequest leave) {
    Employee employee = leave.getEmployee();
    return new LeaveResponse(leave.getId(), new EmployeeSummary(employee.getId(),
        employee.getEmployeeId(), employee.fullName(), employee.getJobTitle(), employee.getDepartment()),
        leave.getLeaveType(), leave.getStartDate(), leave.getEndDate(),
        workingDays(leave.getStartDate(), leave.getEndDate()), leave.getReason(), leave.getStatus(),
        leave.getManagerComment(), leave.getRequestedAt(), leave.getReviewedAt(),
        leave.getReviewedBy() == null ? null : leave.getReviewedBy().fullName());
  }
}

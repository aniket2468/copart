package com.acme.ems.service;
import com.acme.ems.dto.BalanceDtos.*;import com.acme.ems.exception.ApiException;import com.acme.ems.model.*;import com.acme.ems.repository.LeaveBalanceRepository;
import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.Year;import java.util.*;

@Service @Transactional
public class LeaveBalanceService{
  private final LeaveBalanceRepository repo;private final AuditService audit;
  public LeaveBalanceService(LeaveBalanceRepository r,AuditService a){repo=r;audit=a;}
  public void initialize(Employee e){int year=Year.now().getValue();createIfMissing(e,LeaveType.VACATION,15,year);createIfMissing(e,LeaveType.SICK,10,year);createIfMissing(e,LeaveType.PERSONAL,5,year);}
  public List<BalanceResponse> balances(Employee e){initialize(e);return repo.findByEmployeeIdAndYearOrderByLeaveType(e.getId(),Year.now().getValue()).stream().map(this::map).toList();}
  public void validateAvailable(Employee e,LeaveType type,int days){if(!tracked(type))return;LeaveBalance b=getOrCreate(e,type);if(days>b.remaining())throw new ApiException(HttpStatus.CONFLICT,"Insufficient "+type.name().toLowerCase()+" leave balance: "+b.remaining()+" working days remaining");}
  public void deduct(Employee e,LeaveType type,int days,Employee actor,Long requestId){if(!tracked(type))return;int year=Year.now().getValue();getOrCreate(e,type);LeaveBalance b=repo.findForUpdate(e.getId(),type,year).orElseThrow();if(days>b.remaining())throw new ApiException(HttpStatus.CONFLICT,"Insufficient leave balance at approval time");b.setUsed(b.getUsed()+days);repo.save(b);audit.record(actor,AuditAction.LEAVE_BALANCE_UPDATED,"LeaveBalance",b.getId(),"Deducted "+days+" working days for approved leave request #"+requestId+"; "+b.remaining()+" days remain");}
  public BalanceResponse adjust(Employee e,LeaveType type,int allocated,String reason,Employee actor){if(!tracked(type))throw new ApiException(HttpStatus.BAD_REQUEST,"Only Vacation, Sick, and Personal balances are tracked");LeaveBalance b=getOrCreate(e,type);if(allocated<b.getUsed())throw new ApiException(HttpStatus.CONFLICT,"Allocation cannot be lower than used leave ("+b.getUsed()+")");int old=b.getTotalAllocated();b.setTotalAllocated(allocated);repo.save(b);audit.record(actor,AuditAction.LEAVE_BALANCE_UPDATED,"LeaveBalance",b.getId(),"Changed "+e.fullName()+"'s "+type+" allocation from "+old+" to "+allocated+" days. Reason: "+reason.trim());return map(b);}
  private LeaveBalance getOrCreate(Employee e,LeaveType type){initialize(e);return repo.findByEmployeeIdAndLeaveTypeAndYear(e.getId(),type,Year.now().getValue()).orElseThrow();}
  private void createIfMissing(Employee e,LeaveType t,int amount,int year){if(repo.findByEmployeeIdAndLeaveTypeAndYear(e.getId(),t,year).isEmpty()){LeaveBalance b=new LeaveBalance();b.setEmployee(e);b.setLeaveType(t);b.setTotalAllocated(amount);b.setUsed(0);b.setYear(year);repo.save(b);}}
  private boolean tracked(LeaveType t){return t==LeaveType.VACATION||t==LeaveType.SICK||t==LeaveType.PERSONAL;}
  private BalanceResponse map(LeaveBalance b){return new BalanceResponse(b.getId(),b.getLeaveType(),b.getTotalAllocated(),b.getUsed(),b.remaining(),b.getYear());}
}

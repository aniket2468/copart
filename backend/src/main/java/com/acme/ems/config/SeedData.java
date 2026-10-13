package com.acme.ems.config;
import com.acme.ems.model.*;import com.acme.ems.repository.EmployeeRepository;import com.acme.ems.repository.LeaveBalanceRepository;
import org.springframework.boot.CommandLineRunner;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;

@Configuration
public class SeedData{
  @Bean CommandLineRunner seed(EmployeeRepository repo,LeaveBalanceRepository balances,PasswordEncoder encoder){return args->{if(repo.count()>0)return;String hash=encoder.encode("Welcome123!");Employee admin=e("ADM-001","Alex","Morgan","admin@example.com","People Operations Director","People",Role.ADMIN,hash,null);repo.save(admin);Employee sarah=e("MGR-001","Sarah","Chen","sarah@example.com","Engineering Manager","Engineering",Role.MANAGER,hash,null);repo.save(sarah);Employee john=repo.save(e("EMP-001","John","Carter","john@example.com","Software Engineer","Engineering",Role.EMPLOYEE,hash,sarah));Employee emma=repo.save(e("EMP-002","Emma","Wilson","emma@example.com","Product Designer","Design",Role.EMPLOYEE,hash,sarah));for(Employee employee:java.util.List.of(admin,sarah,john,emma)){balance(balances,employee,LeaveType.VACATION,15);balance(balances,employee,LeaveType.SICK,10);balance(balances,employee,LeaveType.PERSONAL,5);}};}
  private void balance(LeaveBalanceRepository repo,Employee employee,LeaveType type,int allocated){LeaveBalance b=new LeaveBalance();b.setEmployee(employee);b.setLeaveType(type);b.setTotalAllocated(allocated);b.setUsed(0);b.setYear(java.time.Year.now().getValue());repo.save(b);}
  private Employee e(String id,String first,String last,String email,String title,String dept,Role role,String hash,Employee manager){Employee e=new Employee();e.setEmployeeId(id);e.setFirstName(first);e.setLastName(last);e.setEmail(email);e.setPasswordHash(hash);e.setJobTitle(title);e.setDepartment(dept);e.setRole(role);e.setManager(manager);e.setDateOfJoining(LocalDate.of(2024,1,15));e.setActive(true);return e;}
}

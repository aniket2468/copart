package com.acme.ems.repository;
import com.acme.ems.model.AuditLog;
import org.springframework.data.jpa.repository.*;
public interface AuditLogRepository extends JpaRepository<AuditLog,Long>,JpaSpecificationExecutor<AuditLog>{}


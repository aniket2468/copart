package com.acme.ems.service;
import com.acme.ems.model.*;import com.acme.ems.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
@Service
public class AuditService{
  private final AuditLogRepository repo;public AuditService(AuditLogRepository r){repo=r;}
  public void record(Employee actor,AuditAction action,String entityType,Long entityId,String description){AuditLog log=new AuditLog();log.setActor(actor);log.setAction(action);log.setEntityType(entityType);log.setEntityId(entityId);log.setDescription(description);repo.save(log);}
}


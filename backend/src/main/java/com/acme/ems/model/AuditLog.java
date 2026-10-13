package com.acme.ems.model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="audit_logs",indexes={@Index(name="idx_audit_timestamp",columnList="timestamp")})
public class AuditLog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_employee_id") private Employee actor;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=80) private AuditAction action;
  @Column(nullable=false,length=80) private String entityType;
  @Column(nullable=false) private Long entityId;
  @Column(nullable=false,length=1500) private String description;
  @Column(nullable=false,updatable=false) private Instant timestamp;
  @PrePersist void create(){timestamp=Instant.now();}
  public Long getId(){return id;} public Employee getActor(){return actor;} public void setActor(Employee v){actor=v;} public AuditAction getAction(){return action;} public void setAction(AuditAction v){action=v;}
  public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;} public Long getEntityId(){return entityId;} public void setEntityId(Long v){entityId=v;}
  public String getDescription(){return description;} public void setDescription(String v){description=v;} public Instant getTimestamp(){return timestamp;}
}

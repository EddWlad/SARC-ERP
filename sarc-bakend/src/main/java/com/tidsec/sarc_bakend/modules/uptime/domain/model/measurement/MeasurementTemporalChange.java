package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

/** Decision temporal append-only. No sustituye revisiones ni aprobaciones de valores. */
@Entity
@Table(name="upt_measurement_temporal_change")
public class MeasurementTemporalChange {
    public static final String POLICY_VERSION="006-1";
    public enum Outcome { APPLIED, REQUIRES_RECONCILIATION, VERIFIED_KNOWN_RANGE }
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private UUID measurementId;
    @Column(nullable=false,updatable=false,length=20) private String policyVersion;
    @Column(nullable=false,updatable=false) private long expectedMeasurementVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=32) private Outcome outcome;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb")
    private Map<String,Object> previousTemporal;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb")
    private Map<String,Object> resultTemporal;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb")
    private Map<String,Object> evidence;
    @Column(updatable=false) private UUID actorId;
    @Column(nullable=false,updatable=false,length=10) private String actorKind;
    @Column(nullable=false,updatable=false) private Instant occurredAt;
    @Column(nullable=false,updatable=false,columnDefinition="text") private String reason;

    protected MeasurementTemporalChange() {}
    public MeasurementTemporalChange(UUID measurementId,long version,Outcome outcome,
            Map<String,Object> before,Map<String,Object> after,Map<String,Object> evidence,
            UUID actor,Instant at,String reason) {
        if (measurementId==null || version<0 || outcome==null || before==null || after==null
                || evidence==null || at==null || reason==null || reason.isBlank())
            throw new IllegalArgumentException("Decision temporal incompleta");
        id=UUID.randomUUID();this.measurementId=measurementId;expectedMeasurementVersion=version;
        policyVersion=POLICY_VERSION;this.outcome=outcome;
        previousTemporal=copy(before);resultTemporal=copy(after);this.evidence=copy(evidence);
        actorId=actor;actorKind=actor==null?"SYSTEM":"USER";occurredAt=at;this.reason=reason;
    }
    private static Map<String,Object> copy(Map<String,Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
    public UUID getId(){return id;}
    public UUID getMeasurementId(){return measurementId;}
    public String getPolicyVersion(){return policyVersion;}
    public long getExpectedMeasurementVersion(){return expectedMeasurementVersion;}
    public Outcome getOutcome(){return outcome;}
    public Map<String,Object> getPreviousTemporal(){return previousTemporal;}
    public Map<String,Object> getResultTemporal(){return resultTemporal;}
    public Map<String,Object> getEvidence(){return evidence;}
    public UUID getActorId(){return actorId;}
    public String getActorKind(){return actorKind;}
    public Instant getOccurredAt(){return occurredAt;}
    public String getReason(){return reason;}
}

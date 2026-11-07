package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;

/** Solicitud durable. Un resultado confirmado es terminal y no se vuelve a aplicar. */
@Entity
@Table(name="upt_temporal_transition_work")
public class TemporalTransitionWork extends BaseAuditableEntity {
    public enum Status {QUEUED,RUNNING,COMPLETED,FAILED,INTERRUPTED}
    public enum Outcome {APPLIED,VERIFIED_KNOWN_RANGE,REQUIRES_RECONCILIATION,ALREADY_APPLIED,STALE_VERSION}
    public record Item(UUID id,long version) {
        public Item {if(id==null||version<0) throw new BusinessRuleException("UPTIME_TRANSITION_ITEMS_INVALID","Indica mediciones y versiones válidas.");}
    }
    public record Result(UUID id,long version,Outcome outcome,UUID eventId) {
        public Result {Objects.requireNonNull(id);Objects.requireNonNull(outcome);if(version<0)throw new IllegalArgumentException("Version inválida");}
    }
    @Column(nullable=false,updatable=false) private UUID requestedBy;
    @Column(nullable=false,updatable=false) private UUID requestKey;
    @Column(nullable=false,updatable=false,length=20) private String policyVersion;
    @Column(nullable=false,updatable=false,length=64) private String requestHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb") private List<Item> items;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Result> results;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false) private int attempt;
    private UUID lastAttemptBy;
    private Instant startedAt;
    private Instant finishedAt;
    @Column(length=64) private String failureCode;
    protected TemporalTransitionWork() {}
    private TemporalTransitionWork(UUID actor,UUID key,String hash,List<Item> items) {
        super(UUID.randomUUID());requestedBy=Objects.requireNonNull(actor);requestKey=Objects.requireNonNull(key);
        if(hash==null||!hash.matches("[0-9a-f]{64}")||items==null||items.isEmpty()
            ||items.stream().map(Item::id).distinct().count()!=items.size())
            throw new BusinessRuleException("UPTIME_TRANSITION_ITEMS_INVALID","Indica una lista no vacía sin mediciones repetidas.");
        policyVersion=MeasurementTemporalChange.POLICY_VERSION;requestHash=hash;this.items=List.copyOf(items);
        results=new LinkedHashMap<>();status=Status.QUEUED;
    }
    public static TemporalTransitionWork open(UUID actor,UUID key,String hash,List<Item> items) {
        return new TemporalTransitionWork(actor,key,hash,items);
    }
    public void begin(UUID actor,Instant at) {
        require(Status.QUEUED);lastAttemptBy=Objects.requireNonNull(actor);attempt++;startedAt=at;
        finishedAt=null;failureCode=null;status=Status.RUNNING;
    }
    public void record(Result result) {
        require(Status.RUNNING);
        if(items.stream().noneMatch(i->i.id().equals(result.id())&&i.version()==result.version())
            ||results.containsKey(result.id().toString()))
            throw new ConflictException("UPTIME_TRANSITION_RESULT_INVALID","El resultado no corresponde a un item pendiente.");
        var copy=new LinkedHashMap<>(results);copy.put(result.id().toString(),result);results=copy;
    }
    public void complete(Instant at) {
        require(Status.RUNNING);
        if(results.size()!=items.size())throw new ConflictException("UPTIME_TRANSITION_INCOMPLETE","Hay mediciones pendientes de procesar.");
        status=Status.COMPLETED;finishedAt=at;
    }
    public void fail(String code,Instant at) {
        if(status!=Status.RUNNING&&status!=Status.QUEUED)return;
        if(!Set.of("UPTIME_TRANSITION_QUEUE_FULL","UPTIME_TRANSITION_FAILED","UPTIME_TRANSITION_AUTHORIZATION_LOST").contains(code))
            throw new IllegalArgumentException("Código técnico no permitido");
        status=Status.FAILED;failureCode=code;finishedAt=at;
    }
    public void interrupt(Instant at) {
        if(status==Status.RUNNING||status==Status.QUEUED){status=Status.INTERRUPTED;finishedAt=at;failureCode=null;}
    }
    public void retry() {
        if(status!=Status.FAILED&&status!=Status.INTERRUPTED)
            throw new ConflictException("UPTIME_TRANSITION_NOT_RETRYABLE","Solo se reintentan trabajos fallidos o interrumpidos.");
        status=Status.QUEUED;failureCode=null;startedAt=null;finishedAt=null;
    }
    private void require(Status value) {if(status!=value)throw new ConflictException("UPTIME_TRANSITION_STATE_CHANGED","El trabajo cambió de estado; recarga sus datos.");}
    public UUID getRequestedBy(){return requestedBy;}
    public UUID getRequestKey(){return requestKey;}
    public String getPolicyVersion(){return policyVersion;}
    public String getRequestHash(){return requestHash;}
    public List<Item> getItems(){return List.copyOf(items);}
    public Map<String,Result> getResults(){return Collections.unmodifiableMap(results);}
    public Status getStatus(){return status;}
    public int getAttempt(){return attempt;}
    public UUID getLastAttemptBy(){return lastAttemptBy;}
    public Instant getStartedAt(){return startedAt;}
    public Instant getFinishedAt(){return finishedAt;}
    public String getFailureCode(){return failureCode;}
}

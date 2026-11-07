package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.*;
import com.tidsec.sarc_bakend.shared.audit.*;
import com.tidsec.sarc_bakend.shared.error.*;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/** Coordinación local recuperable. Cada decisión y su resultado se confirman juntos. */
@Service
@PreAuthorize(UptimePermissions.EDITORS)
public class UptimeTemporalTransitionService {
    public record Submission(TemporalTransitionWork work,boolean repeated) {}
    private final TemporalTransitionWorkRepository works;
    private final MeasurementRepository measurements;
    private final MeasurementTemporalChangeRepository events;
    private final UptimeImportRepository imports;
    private final UptimeTemporalEvidence evidence;
    private final ImportJobs jobs;
    private final AuditTrail audit;
    private final CurrentActor actor;
    private final UptimeReadAccess access;
    private final Clock clock;
    private final TransactionTemplate tx;

    UptimeTemporalTransitionService(TemporalTransitionWorkRepository works,MeasurementRepository measurements,
            MeasurementTemporalChangeRepository events,UptimeImportRepository imports,UptimeTemporalEvidence evidence,
            ImportJobs jobs,AuditTrail audit,CurrentActor actor,UptimeReadAccess access,Clock clock,
            PlatformTransactionManager transactionManager) {
        this.works=works;this.measurements=measurements;this.events=events;this.imports=imports;
        this.evidence=evidence;this.jobs=jobs;this.audit=audit;this.actor=actor;this.access=access;this.clock=clock;
        tx=new TransactionTemplate(transactionManager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Submission submit(UUID key,List<Item> items) {
        UUID author=actor.id().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Sesión requerida"));
        if(key==null||items==null||items.isEmpty()||items.size()>500
            ||items.stream().anyMatch(Objects::isNull)||items.stream().map(Item::id).distinct().count()!=items.size())
            throw new BusinessRuleException("UPTIME_TRANSITION_ITEMS_INVALID","Indica hasta 500 mediciones distintas por bloque y una clave válida.");
        List<Item> canonical=items.stream().sorted(Comparator.comparing(i->i.id().toString())).toList();
        String text=canonical.stream().map(i->i.id()+":"+i.version()).collect(java.util.stream.Collectors.joining(";"));
        String hash=ContentFingerprint.sha256(text.getBytes(StandardCharsets.UTF_8));
        try {
            return tx.execute(status->{
                Optional<TemporalTransitionWork> existing=works.findRequest(author,key);
                if(existing.isPresent())return repeat(existing.get(),hash);
                TemporalTransitionWork work=works.save(TemporalTransitionWork.open(author,key,hash,canonical));
                audit.record("UPTIME_TEMPORAL_TRANSITION_REQUESTED","UPTIME_TEMPORAL_WORK",work.getId(),Map.of("total",String.valueOf(items.size())));
                enqueue(work.getId(),author);return new Submission(work,false);
            });
        } catch(DataIntegrityViolationException concurrent) {
            return tx.execute(status->repeat(works.findRequest(author,key).orElseThrow(()->concurrent),hash));
        }
    }
    private Submission repeat(TemporalTransitionWork work,String hash) {
        if(!work.getRequestHash().equals(hash))throw new ConflictException("UPTIME_TRANSITION_KEY_REUSED","La clave ya corresponde a otras mediciones o versiones.");
        return new Submission(work,true);
    }
    @Transactional(readOnly=true)
    public TemporalTransitionWork get(UUID id){return load(id);}

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TemporalTransitionWork retry(UUID id,long version) {
        UUID author=actor.id().orElseThrow();
        return tx.execute(status->{var work=load(id);work.requireVersion(version);work.retry();works.save(work);
            audit.record("UPTIME_TEMPORAL_TRANSITION_RETRIED","UPTIME_TEMPORAL_WORK",id,Map.of("attempt",String.valueOf(work.getAttempt()+1)));
            enqueue(id,author);return work;});
    }
    private void enqueue(UUID id,UUID author) {
        jobs.submitAfterCommit(()->process(id,author),()->fail(id,null,"UPTIME_TRANSITION_QUEUE_FULL"));
    }

    void process(UUID id,UUID author) {
        if(!access.canReadOperational()||!actor.id().filter(author::equals).isPresent()) {
            fail(id,null,"UPTIME_TRANSITION_AUTHORIZATION_LOST");return;
        }
        TemporalTransitionWork claimed;
        try {
            claimed=tx.execute(status->{var work=load(id);
                if(work.getStatus()!=Status.QUEUED)return null;
                work.begin(author,clock.instant());return works.save(work);});
        } catch(ObjectOptimisticLockingFailureException competing){return;}
        if(claimed==null)return;
        int attempt=claimed.getAttempt();
        Map<UUID,UptimeTemporalEvidence.Inspection> cache=new LinkedHashMap<>(4,0.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID,UptimeTemporalEvidence.Inspection> entry){return size()>2;}
        };
        try {
            for(Item item:claimed.getItems()) {
                if(claimed.getResults().containsKey(item.id().toString()))continue;
                var snapshot=tx.execute(status->snapshot(item.id()));
                boolean needsProof=snapshot!=null&&snapshot.measurement().getVersion()==item.version()
                    &&tx.execute(status->events.history(item.id()).stream().noneMatch(e->sameDecision(snapshot.measurement(),e)));
                var decision=needsProof?evidence.inspect(snapshot,cache):null;
                if(decision!=null && decision.period().precision()!=TemporalPrecision.REQUIRES_RECONCILIATION) {
                    var target=decision.period();
                    var candidates=tx.execute(status->measurements.findTemporalCandidates(
                        snapshot.measurement().getConnectorId(),snapshot.measurement().getSource()).stream()
                        .filter(peer->!peer.getId().equals(item.id())).map(Measurement::getId).toList());
                    for(UUID candidate:candidates) {
                        var peer=tx.execute(status->snapshot(candidate));
                        if(peer!=null && evidence.inspect(peer,cache).period().equals(target)) {
                            decision=UptimeTemporalEvidence.unresolved(snapshot.measurement(),target.referenceWeek(),target.precision(),
                                "CANONICAL_IDENTITY_COLLISION",decision.evidence());break;
                        }
                    }
                }
                try {apply(id,attempt,item,decision);}
                catch(DataIntegrityViolationException|ObjectOptimisticLockingFailureException concurrent) {
                    // La transacción fallida terminó: consultar unicidad/versiones otra vez, sin sobrescribir.
                    apply(id,attempt,item,decision);
                }
            }
            tx.executeWithoutResult(status->{var work=load(id);
                if(work.getStatus()==Status.RUNNING&&work.getAttempt()==attempt){work.complete(clock.instant());works.save(work);}});
        } catch(RuntimeException technical) {fail(id,attempt,"UPTIME_TRANSITION_FAILED");}
    }

    private UptimeTemporalEvidence.Snapshot snapshot(UUID id) {
        Measurement m=measurements.findById(id).orElse(null);if(m==null)return null;
        var chain=measurements.findRevisions(id);
        Map<UUID,UptimeTemporalEvidence.Provenance> sources=new HashMap<>();
        for(var revision:chain)if(revision.getImportBatchId()!=null&&!sources.containsKey(revision.getImportBatchId())) {
            UUID batch=revision.getImportBatchId();sources.put(batch,new UptimeTemporalEvidence.Provenance(
                imports.findBatch(batch).orElse(null),imports.findOriginal(batch).orElse(null),imports.findProvenanceRows(id,batch)));
        }
        return new UptimeTemporalEvidence.Snapshot(m,chain,sources);
    }

    private void apply(UUID workId,int attempt,Item item,UptimeTemporalEvidence.Decision decision) {
        tx.executeWithoutResult(status->{
            var work=load(workId);
            if(work.getStatus()!=Status.RUNNING||work.getAttempt()!=attempt||work.getResults().containsKey(item.id().toString()))return;
            Measurement m=measurements.findById(item.id()).orElse(null);
            Result result;
            if(m==null)result=new Result(item.id(),item.version(),Outcome.STALE_VERSION,null);
            else {
                Optional<MeasurementTemporalChange> terminal=events.history(item.id()).stream()
                    .filter(e->e.getPolicyVersion().equals(MeasurementTemporalChange.POLICY_VERSION)&&sameDecision(m,e)).findFirst();
                if(terminal.isPresent()&&(item.version()==m.getVersion()||item.version()==terminal.get().getExpectedMeasurementVersion()))
                    result=new Result(item.id(),item.version(),Outcome.ALREADY_APPLIED,terminal.get().getId());
                else if(m.getVersion()!=item.version())result=new Result(item.id(),item.version(),Outcome.STALE_VERSION,null);
                else {
                    UptimeTemporalEvidence.Decision selected=decision;
                    if(selected==null)throw new IllegalStateException("Diagnóstico ausente");
                    if(selected.period().precision()!=TemporalPrecision.REQUIRES_RECONCILIATION
                        && collision(m,selected.period()))
                        selected=UptimeTemporalEvidence.unresolved(m,selected.period().referenceWeek(),selected.target(),
                            "CANONICAL_IDENTITY_COLLISION",selected.evidence());
                    MeasurementTemporalChange event=change(m,item.version(),selected,null,workId);
                    result=new Result(item.id(),item.version(),Outcome.valueOf(event.getOutcome().name()),event.getId());
                }
            }
            work.record(result);works.save(work);
            audit.record("UPTIME_TEMPORAL_TRANSITION_ITEM","UPTIME_TEMPORAL_WORK",workId,
                Map.of("measurementId",item.id().toString(),"outcome",result.outcome().name(),"attempt",String.valueOf(attempt)));
        });
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Measurement classify(UUID id,long version,TemporalPrecision precision,LocalDate week,
            Instant start,Instant end,String reason,String proof) {
        if(reason==null||reason.isBlank()||proof==null||proof.isBlank()||precision==null
            ||precision==TemporalPrecision.REQUIRES_RECONCILIATION)
            throw new BusinessRuleException("UPTIME_TEMPORAL_EVIDENCE_REQUIRED","Indica destino, motivo y evidencia de la clasificación temporal.");
        UUID author=actor.id().orElseThrow();
        return tx.execute(status->{
            Measurement m=measurements.findById(id).orElseThrow(()->new NotFoundException("UPTIME_MEASUREMENT_NOT_FOUND","Medición no encontrada."));
            m.requireVersion(version);
            if(precision==TemporalPrecision.REFERENCE_WEEK&&(start!=null||end!=null))
                throw new BusinessRuleException("UPTIME_SELECTION_INVALID","El informe sin horario no admite límites supuestos.");
            MeasurementPeriod period=precision==TemporalPrecision.REFERENCE_WEEK?MeasurementPeriod.reference(week,m.getPeriodZone()):
                MeasurementPeriod.known(start,end,m.getPeriodZone(),week);
            var chain=measurements.findRevisions(id);
            if(precision==TemporalPrecision.REFERENCE_WEEK) {
                if(chain.stream().anyMatch(r->r.getReadingClass()==ReadingClass.MEASURED&&r.getReportedPercent()==null))
                    throw new BusinessRuleException("UPTIME_PERCENT_REQUIRED","La cadena debe conservar su porcentaje fuente antes de clasificarla sin horario.");
            } else for(var r:chain)period.validate(r.getPeriodType(),r.getExactConfirmedAt()!=null,clock.instant());
            if(collision(m,period))throw new ConflictException("UPTIME_TEMPORAL_IDENTITY_CONFLICT","Otra medición ocupa esa identidad. Concilia explícitamente los registros; no se fusionarán.");
            var decision=new UptimeTemporalEvidence.Decision(period,precision,
                precision==TemporalPrecision.REFERENCE_WEEK?MeasurementTemporalChange.Outcome.APPLIED:MeasurementTemporalChange.Outcome.VERIFIED_KNOWN_RANGE,
                Map.of("manualEvidence",proof),reason.strip());
            change(m,version,decision,author,null);return m;
        });
    }
    private MeasurementTemporalChange change(Measurement m,long version,UptimeTemporalEvidence.Decision decision,UUID author,UUID workId) {
        var before=temporal(m);m.changePeriod(decision.period());
        var after=new LinkedHashMap<>(temporal(m));if(decision.target()!=null)after.put("targetPrecision",decision.target().name());
        Map<String,Object> proof=new LinkedHashMap<>(decision.evidence());
        if(workId!=null)proof.put("workId",workId.toString());
        var event=new MeasurementTemporalChange(m.getId(),version,decision.outcome(),before,after,proof,author,clock.instant(),decision.reason());
        measurements.saveTemporalDecision(m);events.append(event);
        audit.record("UPTIME_TEMPORAL_CLASSIFIED","UPTIME_MEASUREMENT",m.getId(),
            Map.of("eventId",event.getId().toString(),"outcome",event.getOutcome().name(),"actorKind",event.getActorKind()));
        return event;
    }
    private boolean collision(Measurement m,MeasurementPeriod period) {
        return measurements.findByIdentity(m.getConnectorId(),m.getSource(),period).filter(other->!other.getId().equals(m.getId())).isPresent();
    }
    private static boolean sameDecision(Measurement m,MeasurementTemporalChange event) {
        Map<String,Object> current=temporal(m),after=event.getResultTemporal();
        return List.of("precision","start","end","referenceWeek","latestRevision").stream()
            .allMatch(key->Objects.equals(String.valueOf(current.get(key)),String.valueOf(after.get(key))));
    }
    static Map<String,Object> temporal(Measurement m) {
        Map<String,Object> value=new LinkedHashMap<>();value.put("precision",m.getTemporalPrecision().name());
        value.put("start",m.getPeriodStart()==null?null:m.getPeriodStart().toString());
        value.put("end",m.getPeriodEnd()==null?null:m.getPeriodEnd().toString());
        value.put("referenceWeek",m.getReferenceWeek()==null?null:m.getReferenceWeek().toString());
        value.put("latestRevision",m.getLatestRevision());value.put("effectiveRevision",m.getEffectiveRevision());
        value.put("excluded",m.isExcluded());return value;
    }
    private void fail(UUID id,Integer attempt,String code) {
        tx.executeWithoutResult(status->{var work=load(id);
            if(attempt==null||work.getAttempt()==attempt){work.fail(code,clock.instant());works.save(work);}});
    }
    private TemporalTransitionWork load(UUID id) {
        return works.findById(id).orElseThrow(()->new NotFoundException("UPTIME_TRANSITION_NOT_FOUND","Trabajo de transición no encontrado."));
    }
}

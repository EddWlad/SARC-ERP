package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser;
import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser.ParsedRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Verifica originales fuera de la transacción de escritura; nunca decide por la etiqueta FILE sola. */
@Component
class UptimeTemporalEvidence {
    record Provenance(UptimeImportBatch batch,UptimeImportRepository.Original original,List<UptimeImportRow> rows) {}
    record Snapshot(Measurement measurement,List<MeasurementRevision> chain,Map<UUID,Provenance> provenance) {}
    record Inspection(List<ParsedRow> rows,String failure) {}
    record Decision(MeasurementPeriod period,TemporalPrecision target,MeasurementTemporalChange.Outcome outcome,
            Map<String,Object> evidence,String reason) {}
    private final FileStorage storage;
    private final TabularReader reader;
    private final UptimeRowParser parser;
    private final OperatingZone zone;
    private final Clock clock;
    UptimeTemporalEvidence(FileStorage storage,TabularReader reader,UptimeRowParser parser,OperatingZone zone,Clock clock) {
        this.storage=storage;this.reader=reader;this.parser=parser;this.zone=zone;this.clock=clock;
    }

    Decision inspect(Snapshot snapshot,Map<UUID,Inspection> cache) {
        Measurement m=snapshot.measurement();
        if(snapshot.chain().isEmpty())return unresolved(m,null,null,"CHAIN_MISSING",Map.of());
        Set<LocalDate> weeks=new HashSet<>();Set<TemporalPrecision> targets=new HashSet<>();
        List<Map<String,Object>> proof=new ArrayList<>();
        for(MeasurementRevision revision:snapshot.chain()) {
            if(revision.getOrigin()!=MeasurementOrigin.FILE && revision.getOrigin()!=MeasurementOrigin.PASTE)
                return unresolved(m,null,null,"MIXED_OR_MANUAL_CHAIN",Map.of("revisions",snapshot.chain().size()));
            Provenance source=snapshot.provenance().get(revision.getImportBatchId());
            if(source==null || source.batch()==null || source.original()==null)
                return unresolved(m,null,null,"ORIGINAL_MISSING",Map.of());
            Inspection inspected=cache.computeIfAbsent(source.batch().getId(),ignored->read(source));
            if(inspected.failure()!=null)return unresolved(m,null,null,inspected.failure(),Map.of());
            List<UptimeImportRow> stored=source.rows().stream()
                .filter(row->Objects.equals(row.getConnectorId(),m.getConnectorId()))
                .filter(row->Objects.equals(row.getSourceValues(),revision.getSourceValues()))
                .filter(row->sameValues(revision,row)).toList();
            if(stored.size()!=1)return unresolved(m,null,null,"SOURCE_ROW_AMBIGUOUS",Map.of());
            UptimeImportRow link=stored.getFirst();
            List<ParsedRow> matching=inspected.rows().stream()
                .filter(row->Objects.equals(row.sheet(),link.getSourceSheet())
                    && Objects.equals(row.sheetRow(),link.getSourceRow()) && row.rowNumber()==link.getRowNumber()).toList();
            if(matching.size()!=1)return unresolved(m,null,null,"SOURCE_COORDINATES_DIFFER",Map.of());
            ParsedRow row=matching.getFirst();
            if(!row.isValid() || !sameValues(revision,row) || !sameRaw(revision.getSourceValues(),row.sourceValues())
                || !Objects.equals(row.site(),link.getSourceSite()) || !Objects.equals(row.charger(),link.getSourceCharger())
                || !Objects.equals(row.connector(),link.getSourceConnector()))
                return unresolved(m,null,null,"SOURCE_VALUES_DIFFER",Map.of());
            boolean hasStart=Boolean.TRUE.equals(row.sourceValues().get("sourcePeriodStartPresent"));
            boolean hasEnd=Boolean.TRUE.equals(row.sourceValues().get("sourcePeriodEndPresent"));
            if(hasStart!=hasEnd || hasStart && (row.periodStart()==null||row.periodEnd()==null))
                return unresolved(m,null,null,"SOURCE_RANGE_INCOMPLETE",Map.of());
            LocalDate week=reference(source.batch());
            if(week!=null)weeks.add(week);
            if(!hasStart) {
                targets.add(TemporalPrecision.REFERENCE_WEEK);
                if(week==null)return unresolved(m,null,null,"REFERENCE_NOT_PROVEN",Map.of());
            } else {
                targets.add(TemporalPrecision.KNOWN_RANGE);
                if(!Objects.equals(row.periodStart(),m.getPeriodStart())||!Objects.equals(row.periodEnd(),m.getPeriodEnd()))
                    return unresolved(m,week,null,"SOURCE_RANGE_DIFFERS",Map.of());
            }
            Map<String,Object> item=new LinkedHashMap<>();item.put("revision",revision.getRevision());
            item.put("batchId",source.batch().getId().toString());item.put("checksum",source.original().checksum());
            item.put("sheet",row.sheet());item.put("row",row.sheetRow());proof.add(item);
        }
        LocalDate week=weeks.size()==1?weeks.iterator().next():null;
        TemporalPrecision target=targets.size()==1?targets.iterator().next():null;
        Map<String,Object> evidence=Map.of("proof",List.copyOf(proof),"chainSize",snapshot.chain().size());
        if(weeks.size()>1 || targets.size()!=1)return unresolved(m,week,null,"MIXED_TEMPORAL_CHAIN",evidence);
        if(target==TemporalPrecision.REFERENCE_WEEK) {
            if(week==null)return unresolved(m,null,null,"REFERENCE_NOT_PROVEN",evidence);
            if(snapshot.chain().stream().anyMatch(r->r.getReadingClass()==ReadingClass.MEASURED&&r.getReportedPercent()==null))
                return unresolved(m,week,target,"SOURCE_PERCENT_MISSING",evidence);
            return new Decision(MeasurementPeriod.reference(week,m.getPeriodZone()),target,
                MeasurementTemporalChange.Outcome.APPLIED,evidence,"Originales y cadena verificados: informe sin horario real.");
        }
        try {
            MeasurementPeriod known=MeasurementPeriod.known(m.getPeriodStart(),m.getPeriodEnd(),m.getPeriodZone(),week);
            for(var revision:snapshot.chain())known.validate(revision.getPeriodType(),revision.getExactConfirmedAt()!=null,clock.instant());
            return new Decision(known,target,MeasurementTemporalChange.Outcome.VERIFIED_KNOWN_RANGE,evidence,
                "Los originales acreditan los límites reales de toda la cadena.");
        } catch(RuntimeException invalid) {
            return unresolved(m,week,target,"SOURCE_RANGE_INVALID",evidence);
        }
    }

    private Inspection read(Provenance source) {
        try {
            byte[] content=storage.readDocument(source.original().reference());
            if(!ContentFingerprint.sha256(content).equals(source.original().checksum()))return new Inspection(List.of(),"CHECKSUM_DIFFERS");
            return new Inspection(parser.inspectSource(reader.readSheets(content)),null);
        } catch(RuntimeException unavailable) {return new Inspection(List.of(),"ORIGINAL_UNAVAILABLE");}
    }
    private LocalDate reference(UptimeImportBatch batch) {
        if(batch.getReferenceWeek()!=null)return batch.getReferenceWeek();
        Instant start=batch.getDeclaredPeriodStart(),end=batch.getDeclaredPeriodEnd();
        if(batch.getDeclaredPeriodType()!=PeriodType.ROLLING_7D || start==null||end==null)return null;
        LocalDate monday=zone.dateOf(start);
        return monday.getDayOfWeek()==DayOfWeek.MONDAY && zone.startOf(monday).equals(start)
            && zone.startOf(monday.plusWeeks(1)).equals(end)?monday:null;
    }
    static Decision unresolved(Measurement m,LocalDate week,TemporalPrecision target,String code,Map<String,Object> evidence) {
        Map<String,Object> details=new LinkedHashMap<>(evidence);details.put("code",code);
        return new Decision(new MeasurementPeriod(TemporalPrecision.REQUIRES_RECONCILIATION,m.getPeriodStart(),
            m.getPeriodEnd(),m.getPeriodZone(),week,week==null?null:PeriodType.ROLLING_7D),target,
            MeasurementTemporalChange.Outcome.REQUIRES_RECONCILIATION,details,
            "La evidencia temporal requiere conciliación explícita: "+code+".");
    }
    private static boolean numeric(BigDecimal parsed,Integer stored) {
        return parsed==null?stored==null:stored!=null&&parsed.compareTo(BigDecimal.valueOf(stored))==0;
    }
    private static boolean decimal(BigDecimal left,BigDecimal right) {
        return left==null?right==null:right!=null&&left.compareTo(right)==0;
    }
    private static boolean sameValues(MeasurementRevision r,ParsedRow row) {
        var v=row.values();return numeric(v.available(),r.getAvailableMinutes())&&numeric(v.occupied(),r.getOccupiedMinutes())
            &&numeric(v.fault(),r.getFaultMinutes())&&numeric(v.offline(),r.getOfflineMinutes())
            &&decimal(v.reportedPercent(),r.getReportedPercent())&&v.basis()==r.getUptimeBasis();
    }
    private static boolean sameValues(MeasurementRevision r,UptimeImportRow row) {
        return Objects.equals(r.getAvailableMinutes(),row.getAvailableMinutes())&&Objects.equals(r.getOccupiedMinutes(),row.getOccupiedMinutes())
            &&Objects.equals(r.getFaultMinutes(),row.getFaultMinutes())&&Objects.equals(r.getOfflineMinutes(),row.getOfflineMinutes())
            &&decimal(r.getReportedPercent(),row.getReportedPercent())&&r.getUptimeBasis()==row.getUptimeBasis();
    }
    private static boolean sameRaw(Map<String,Object> old,Map<String,Object> inspected) {
        return old!=null&&!old.isEmpty()&&old.entrySet().stream().allMatch(e->Objects.equals(String.valueOf(e.getValue()),String.valueOf(inspected.get(e.getKey()))));
    }
}

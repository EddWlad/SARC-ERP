package com.tidsec.sarc_bakend.modules.uptime.application.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.support.Feature006Fixtures;
import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
@SpringBootTest(properties="sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class UptimeTemporalEvidenceTest {
    @Autowired UptimeTemporalEvidence evidence;
    @Autowired UptimeRowParser parser;
    @Autowired TabularReader reader;
    @MockitoBean FileStorage storage;
    private static final Instant START=Instant.parse("2026-10-26T05:00:00Z");
    private UptimeTemporalEvidence.Snapshot fixture(boolean exclude) throws Exception {
        return fixture(Feature006Fixtures.weeklyWorkbook(),exclude);
    }
    private UptimeTemporalEvidence.Snapshot fixture(byte[] file,boolean exclude) throws Exception {
        var parsed=parser.inspectSource(reader.readSheets(file)).getFirst();
        UUID batchId=UUID.randomUUID();String reference="private/"+batchId;
        when(storage.readDocument(reference)).thenReturn(file);
        var batch=UptimeImportBatch.openWeekly(batchId,MeasurementSource.ENERLINK,START,START.plusSeconds(604800),"semanal.xlsx",ContentFingerprint.sha256(file),reference);
        Map<String,Object> raw=new HashMap<>(parsed.sourceValues());raw.remove("sourcePeriodStartPresent");raw.remove("sourcePeriodEndPresent");
        var a=MeasurementRules.assess(parsed.values(),10080,PeriodType.ROLLING_7D);
        var m=Measurement.open(UUID.randomUUID(),UUID.randomUUID(),MeasurementSource.ENERLINK,START,START.plusSeconds(604800),"America/Guayaquil");
        var revision=m.register(new RevisionData(ReadingClass.MEASURED,PeriodType.EXACT_WEEK,a.available(),a.occupied(),a.fault(),a.offline(),
            parsed.values().reportedPercent(),a.observations(),null,MeasurementOrigin.FILE,batchId,raw,"Anterior","123456","Tipo2",null,START,null,START,a.basis()));
        m.validate(revision,null,null,START,null);if(exclude)m.exclude();
        m.changePeriod(new MeasurementPeriod(TemporalPrecision.REQUIRES_RECONCILIATION,START,START.plusSeconds(604800),"America/Guayaquil",null,null));
        var row=UptimeImportRow.read(batchId,parsed.rowNumber(),parsed.sheet(),parsed.sheetRow(),parsed.site(),parsed.charger(),parsed.connector(),START,START.plusSeconds(604800),raw);
        row.assess(PeriodType.ROLLING_7D,a,parsed.values().reportedPercent());row.classify(UptimeRowClassification.NEW,m.getEquipmentId(),m.getConnectorId(),null,null,List.of());
        row.recordOutcome(UptimeRowOutcome.APPLIED,m.getId());
        return new UptimeTemporalEvidence.Snapshot(m,List.of(revision),Map.of(batchId,new UptimeTemporalEvidence.Provenance(batch,new UptimeImportRepository.Original(reference,ContentFingerprint.sha256(file)),List.of(row))));
    }
    @Test void originalWorkbookProvesReferenceWithoutResettingValuesOrValidation() throws Exception {
        var snapshot=fixture(true);var r=snapshot.chain().getFirst();
        var result=evidence.inspect(snapshot,new HashMap<>());
        assertThat(result.outcome()).isEqualTo(MeasurementTemporalChange.Outcome.APPLIED);
        assertThat(result.period().precision()).isEqualTo(TemporalPrecision.REFERENCE_WEEK);
        assertThat(result.period().referenceWeek()).isEqualTo(LocalDate.of(2026,10,26));assertThat(result.period().start()).isNull();
        assertThat(r.getReviewState()).isEqualTo(ReviewState.VALIDATED);assertThat(r.getValidatedAt()).isEqualTo(START);
        assertThat(r.getPeriodType()).isEqualTo(PeriodType.EXACT_WEEK);assertThat(snapshot.measurement().isExcluded()).isTrue();
    }
    @Test void changedChecksumRequiresReconciliation() throws Exception {
        var snapshot=fixture(false);when(storage.readDocument(anyString())).thenReturn(new byte[]{1,2,3});
        assertThat(evidence.inspect(snapshot,new HashMap<>()).evidence()).containsEntry("code","CHECKSUM_DIFFERS");
    }
    @Test void inaccessibleOriginalDoesNotExposeProviderException() throws Exception {
        var snapshot=fixture(false);when(storage.readDocument(anyString())).thenThrow(new IllegalStateException("provider-secret"));
        var decision=evidence.inspect(snapshot,new HashMap<>());
        assertThat(decision.outcome()).isEqualTo(MeasurementTemporalChange.Outcome.REQUIRES_RECONCILIATION);
        assertThat(decision.evidence().toString()).doesNotContain("provider-secret");
    }
    @Test void missingRowAndMissingOriginalAreNotInferredFromFileOrigin() throws Exception {
        var snapshot=fixture(false);UUID key=snapshot.provenance().keySet().iterator().next();var source=snapshot.provenance().get(key);
        var noRows=new UptimeTemporalEvidence.Snapshot(snapshot.measurement(),snapshot.chain(),Map.of(key,new UptimeTemporalEvidence.Provenance(source.batch(),source.original(),List.of())));
        assertThat(evidence.inspect(noRows,new HashMap<>()).evidence()).containsEntry("code","SOURCE_ROW_AMBIGUOUS");
        assertThat(evidence.inspect(new UptimeTemporalEvidence.Snapshot(snapshot.measurement(),snapshot.chain(),Map.of()),new HashMap<>()).evidence()).containsEntry("code","ORIGINAL_MISSING");
    }
    @Test void explicitSourceBoundsAreVerifiedAsKnownWithoutUsingReferenceAsProof() throws Exception {
        String csv="Cargador\tConector\tDesde\tHasta\tDisponible\tOcupado\tCon falla\tFuera de linea\tUptime\n"+
            "QA\t1\t26-10-2026 00:00:00\t02-11-2026 00:00:00\t9815\t31\t0\t234\t97.68";
        var snapshot=fixture(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),false);
        var decision=evidence.inspect(snapshot,new HashMap<>());
        assertThat(decision.outcome()).isEqualTo(MeasurementTemporalChange.Outcome.VERIFIED_KNOWN_RANGE);
        assertThat(decision.period().start()).isEqualTo(START);assertThat(decision.period().end()).isEqualTo(START.plusSeconds(604800));
    }
    @Test void wrongCoordinatesAndValuesRequireReconciliation() throws Exception {
        var snapshot=fixture(false);UUID key=snapshot.provenance().keySet().iterator().next();var source=snapshot.provenance().get(key);var old=source.rows().getFirst();
        var wrong=UptimeImportRow.read(key,old.getRowNumber(),old.getSourceSheet(),old.getSourceRow()+1,old.getSourceSite(),old.getSourceCharger(),old.getSourceConnector(),START,START.plusSeconds(604800),old.getSourceValues());
        var revision=snapshot.chain().getFirst();
        var a=new MeasurementRules.Assessment(revision.getAvailableMinutes(),revision.getOccupiedMinutes(),revision.getFaultMinutes(),revision.getOfflineMinutes(),List.of(),revision.getReportedPercent(),revision.getUptimeBasis());
        wrong.assess(PeriodType.ROLLING_7D,a,revision.getReportedPercent());wrong.classify(UptimeRowClassification.NEW,snapshot.measurement().getEquipmentId(),snapshot.measurement().getConnectorId(),null,null,List.of());
        var changed=new UptimeTemporalEvidence.Snapshot(snapshot.measurement(),snapshot.chain(),Map.of(key,new UptimeTemporalEvidence.Provenance(source.batch(),source.original(),List.of(wrong))));
        assertThat(evidence.inspect(changed,new HashMap<>()).evidence()).containsEntry("code","SOURCE_COORDINATES_DIFFER");
        var different=new MeasurementRules.Assessment(1,a.occupied(),a.fault(),a.offline(),List.of(),a.percent(),a.basis());
        old.assess(PeriodType.ROLLING_7D,different,revision.getReportedPercent());
        assertThat(evidence.inspect(snapshot,new HashMap<>()).outcome()).isEqualTo(MeasurementTemporalChange.Outcome.REQUIRES_RECONCILIATION);
    }
    @Test void mixedManualCorrectionPreservesEffectiveValidationAndRemainsUnresolved() throws Exception {
        var snapshot=fixture(false);var r=snapshot.chain().getFirst();var m=snapshot.measurement();
        var manual=m.correct(new RevisionData(r.getReadingClass(),r.getPeriodType(),r.getAvailableMinutes(),r.getOccupiedMinutes(),r.getFaultMinutes(),r.getOfflineMinutes(),r.getReportedPercent(),r.getObservations(),"Corrección",MeasurementOrigin.MANUAL,null,r.getSourceValues(),r.getEquipmentLabel(),r.getEquipmentCode(),r.getConnectorTypeLabel(),null,START,null,START,r.getUptimeBasis()),r,START);
        var mixed=new UptimeTemporalEvidence.Snapshot(m,List.of(manual,r),snapshot.provenance());
        assertThat(evidence.inspect(mixed,new HashMap<>()).evidence()).containsEntry("code","MIXED_OR_MANUAL_CHAIN");
        assertThat(m.getEffectiveRevision()).isEqualTo(1);assertThat(r.getReviewState()).isEqualTo(ReviewState.VALIDATED);
    }

    @Test void sourceWithoutPercentNeverGeneratesOneForReference() throws Exception {
        String csv="Cargador\tConector\tDisponible\tOcupado\tCon falla\tFuera de linea\tUptime\nQA\t1\t9815\t31\t0\t234\t";
        var snapshot=fixture(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),false);
        assertThat(snapshot.chain().getFirst().getReportedPercent()).isNull();
        assertThat(evidence.inspect(snapshot,new HashMap<>()).evidence()).containsEntry("code","SOURCE_PERCENT_MISSING");
    }
    @Test void blockCorrectionDoesNotUseAnEarlierFileAsProofOfTheWholeChain() throws Exception {
        var snapshot=fixture(false);var r=snapshot.chain().getFirst();var m=snapshot.measurement();
        var block=m.correct(new RevisionData(r.getReadingClass(),r.getPeriodType(),r.getAvailableMinutes(),r.getOccupiedMinutes(),r.getFaultMinutes(),r.getOfflineMinutes(),r.getReportedPercent(),r.getObservations(),"Bloque pegado",MeasurementOrigin.BLOCK,null,r.getSourceValues(),r.getEquipmentLabel(),r.getEquipmentCode(),r.getConnectorTypeLabel(),null,START,null,START,r.getUptimeBasis()),r,START);
        assertThat(evidence.inspect(new UptimeTemporalEvidence.Snapshot(m,List.of(block,r),snapshot.provenance()),new HashMap<>()).evidence())
            .containsEntry("code","MIXED_OR_MANUAL_CHAIN");
        assertThat(m.getEffectiveRevision()).isEqualTo(1);assertThat(m.getLatestRevision()).isEqualTo(2);
    }
}

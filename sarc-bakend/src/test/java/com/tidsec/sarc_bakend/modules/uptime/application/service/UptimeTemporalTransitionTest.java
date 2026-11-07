package com.tidsec.sarc_bakend.modules.uptime.application.service;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;

class UptimeTemporalTransitionTest {
    private static final Instant NOW=Instant.parse("2026-11-05T16:00:00Z");
    private final UUID actor=UUID.randomUUID();
    private final UUID id=UUID.randomUUID();
    private TemporalTransitionWork work() {
        return TemporalTransitionWork.open(actor,UUID.randomUUID(),"a".repeat(64),
            List.of(new TemporalTransitionWork.Item(id,3)));
    }
    @Test void durableRequestStartsWithoutResultsAndCannotCompleteEarly() {
        var w=work();assertThat(w.getStatus()).isEqualTo(TemporalTransitionWork.Status.QUEUED);
        assertThat(w.getResults()).isEmpty();assertThat(w.getAttempt()).isZero();
        w.begin(actor,NOW);assertThatThrownBy(()->w.complete(NOW)).isInstanceOf(RuntimeException.class);
    }
    @Test void retryKeepsOriginalVersionsAndTerminalResults() {
        var w=work();w.begin(actor,NOW);
        var result=new TemporalTransitionWork.Result(id,3,TemporalTransitionWork.Outcome.APPLIED,UUID.randomUUID());
        w.record(result);w.interrupt(NOW);w.retry();
        UUID second=UUID.randomUUID();w.begin(second,NOW.plusSeconds(1));
        assertThat(w.getItems().getFirst().version()).isEqualTo(3);
        assertThat(w.getResults().get(id.toString())).isEqualTo(result);
        assertThat(w.getRequestedBy()).isEqualTo(actor);assertThat(w.getLastAttemptBy()).isEqualTo(second);
        assertThat(w.getAttempt()).isEqualTo(2);w.complete(NOW.plusSeconds(2));
        assertThat(w.getStatus()).isEqualTo(TemporalTransitionWork.Status.COMPLETED);
        assertThatThrownBy(w::retry).isInstanceOf(RuntimeException.class);
    }
    @Test void cannotOverwriteTerminalOrRecordForeignItem() {
        var w=work();w.begin(actor,NOW);
        w.record(new TemporalTransitionWork.Result(id,3,TemporalTransitionWork.Outcome.STALE_VERSION,null));
        assertThatThrownBy(()->w.record(new TemporalTransitionWork.Result(id,3,TemporalTransitionWork.Outcome.APPLIED,UUID.randomUUID())))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->w.record(new TemporalTransitionWork.Result(UUID.randomUUID(),3,TemporalTransitionWork.Outcome.STALE_VERSION,null)))
            .isInstanceOf(RuntimeException.class);
    }
}

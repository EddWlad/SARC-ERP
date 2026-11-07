package com.tidsec.sarc_bakend.modules.uptime.api.response;
import java.time.Instant;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
import com.tidsec.sarc_bakend.shared.pagination.*;
public record TemporalTransitionWorkResponse(UUID workId,long version,TemporalTransitionWork.Status status,
        String policyVersion,int total,int processed,Map<TemporalTransitionWork.Outcome,Integer> counts,int attempt,
        UUID requestedBy,@Schema(nullable=true) UUID lastAttemptBy,Instant createdAt,
        @Schema(nullable=true) Instant startedAt,@Schema(nullable=true) Instant finishedAt,
        @Schema(nullable=true) String failureCode,Instant cutOff,PageResult<ItemResult> results) {
    public record ItemResult(UUID id,long version,TemporalTransitionWork.Outcome outcome,@Schema(nullable=true) UUID eventId) {}
    public static TemporalTransitionWorkResponse from(TemporalTransitionWork work,PageQuery page,Instant cutOff) {
        var all=work.getItems().stream().map(i->work.getResults().get(i.id().toString())).filter(Objects::nonNull)
            .map(r->new ItemResult(r.id(),r.version(),r.outcome(),r.eventId())).toList();
        Map<TemporalTransitionWork.Outcome,Integer> counts=new EnumMap<>(TemporalTransitionWork.Outcome.class);
        for(var outcome:TemporalTransitionWork.Outcome.values())counts.put(outcome,0);
        all.forEach(result->counts.merge(result.outcome(),1,Integer::sum));
        int start=(int)Math.min((long)page.page()*page.size(),all.size()),end=Math.min(start+page.size(),all.size());
        var results=new PageResult<>(all.subList(start,end),page.page(),page.size(),all.size(),(int)Math.ceil((double)all.size()/page.size()));
        return new TemporalTransitionWorkResponse(work.getId(),work.getVersion(),work.getStatus(),work.getPolicyVersion(),
            work.getItems().size(),all.size(),counts,work.getAttempt(),work.getRequestedBy(),work.getLastAttemptBy(),
            work.getCreatedAt(),work.getStartedAt(),work.getFinishedAt(),work.getFailureCode(),cutOff,results);
    }
}

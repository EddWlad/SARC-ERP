package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.util.*;
import java.util.function.Function;

/** Sumas conocidas y cobertura por estado. Ausencia nunca se convierte en cero. */
public record StateComposition(Long availableMinutes,Long occupiedMinutes,Long faultMinutes,Long offlineMinutes,
        long totalKnownMinutes,int selectedMeasurements,int completeMeasurements,
        Map<String,Integer> knownMeasurementsByState,boolean isComplete) {
    public static StateComposition from(Collection<MeasurementFact> candidates) {
        var facts=candidates.stream().filter(MeasurementFact::included).toList();
        List<Function<MeasurementFact,Long>> values=List.of(MeasurementFact::availableSourceMinutes,
            MeasurementFact::occupiedSourceMinutes,MeasurementFact::faultSourceMinutes,MeasurementFact::offlineSourceMinutes);
        List<String> names=List.of("available","occupied","fault","offline");
        Map<String,Integer> counts=new LinkedHashMap<>();Long[] sums=new Long[4];long total=0;
        for(int n=0;n<4;n++){
            var extract=values.get(n);long sum=0;int known=0;
            for(var fact:facts){Long v=extract.apply(fact);if(v!=null){known++;sum=Math.addExact(sum,v);}}
            counts.put(names.get(n),known);sums[n]=known==0?null:sum;total=Math.addExact(total,sum);
        }
        int complete=(int)facts.stream().filter(f->values.stream().allMatch(extract->extract.apply(f)!=null)).count();
        return new StateComposition(sums[0],sums[1],sums[2],sums[3],total,facts.size(),complete,
            Map.copyOf(counts),!facts.isEmpty()&&complete==facts.size());
    }
}

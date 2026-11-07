package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReferenceReportKpis;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.StateComposition;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Fuente unica de indicadores (data-model §8 y §9). Dashboard, PDF y Excel presentan estas cifras. La
 * unidad es el conector en provisional y el par conector-semana en oficial.
 */
public final class UptimeCalculator {

	public enum UnitState {
		VALIDATED,
		PENDING_REVIEW,
		NO_DATA,
		WITHOUT_MEASUREMENT,
		OUT_OF_SCOPE,
		RETIRED,
		EXCLUDED
	}

	private static final Set<UnitState> OUTSIDE_DENOMINATOR = Set.of(UnitState.OUT_OF_SCOPE, UnitState.RETIRED);
	private static final Set<UnitState> LISTED = Set.of(UnitState.NO_DATA, UnitState.OUT_OF_SCOPE, UnitState.RETIRED);
	private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/** {@code fact} es la medicion que entra al indicador, o nula. */
	public record Unit(MeasurableConnector connector, Instant weekStart, UnitState state, MeasurementFact fact) {
	}

	public record Kpis(BigDecimal uptimePercent, long evaluatedMinutes, BigDecimal offlineHours, BigDecimal faultHours,
			int equipmentEvaluated, int equipmentWithoutData, int connectorsIncluded, int connectorsInScope) {
	}

	public record Coverage(BigDecimal percent, int included, int denominator, Map<UnitState, Integer> byState) {
	}

	public record ConnectorResult(int position, MeasurableConnector connector, BigDecimal uptimePercent,
			Long evaluatedMinutes, Long offlineMinutes, Long faultMinutes, Instant from, Instant to,
            LocalDate referenceWeek,TemporalPrecision temporalPrecision,String aggregationMethod) {
        public ConnectorResult(int position,MeasurableConnector connector,BigDecimal percent,long evaluated,
                long offline,long fault,Instant from,Instant to) {
            this(position,connector,percent,evaluated,offline,fault,from,to,null,
                TemporalPrecision.KNOWN_RANGE,"WEIGHTED_MINUTES");
        }
    }

	public record EquipmentResult(MeasurableConnector sample, BigDecimal uptimePercent, Long evaluatedMinutes,
            Long offlineMinutes, Long faultMinutes, int connectorsIncluded, int connectorsInScope,
            LocalDate referenceWeek,TemporalPrecision temporalPrecision,String aggregationMethod) {
        public EquipmentResult(MeasurableConnector sample,BigDecimal percent,long evaluated,long offline,long fault,
                int included,int inScope) {
            this(sample,percent,evaluated,offline,fault,included,inScope,null,TemporalPrecision.KNOWN_RANGE,"WEIGHTED_MINUTES");
        }
    }

	public record Exclusion(MeasurableConnector connector, UnitState state) {
	}

	private UptimeCalculator() {
	}

	public static List<Unit> officialUnits(Collection<MeasurableConnector> connectors,
			Collection<MeasurementFact> facts, Collection<Instant> weeks) {
		Map<UUID, List<MeasurementFact>> byConnector = byConnector(facts);
		List<Unit> units = new ArrayList<>();
		for (Instant week : weeks) {
			for (MeasurableConnector connector : connectors) {
				List<MeasurementFact> ofWeek = byConnector.getOrDefault(connector.connectorId(), List.of()).stream()
						.filter(fact -> fact.temporalPrecision()==TemporalPrecision.KNOWN_RANGE && fact.periodType().isExact() && fact.periodStart().equals(week)).toList();
				unit(connector, week, ofWeek).ifPresent(units::add);
			}
		}
		return units;
	}

	/** {@code facts} ya viene filtrado por tipo y rango. */
	public static List<Unit> provisionalUnits(Collection<MeasurableConnector> connectors,
			Collection<MeasurementFact> facts) {
		Map<UUID, List<MeasurementFact>> byConnector = byConnector(facts);
		return connectors.stream()
				.map(connector -> unit(connector, null, byConnector.getOrDefault(connector.connectorId(), List.of())))
				.flatMap(Optional::stream).toList();
	}

	private static Map<UUID, List<MeasurementFact>> byConnector(Collection<MeasurementFact> facts) {
		return facts.stream().collect(Collectors.groupingBy(MeasurementFact::connectorId));
	}

	/** La mas reciente incluida define la unidad; sin ella, la precedencia de FR-017. */
	private static Optional<Unit> unit(MeasurableConnector connector, Instant week, List<MeasurementFact> facts) {
		// Una colision acreditada no elige una lectura ganadora por fecha o por validacion previa.
        if (facts.stream().anyMatch(f->f.temporalPrecision()==TemporalPrecision.REQUIRES_RECONCILIATION && !f.excluded()))
            return Optional.of(new Unit(connector,week,UnitState.PENDING_REVIEW,null));
        Optional<MeasurementFact> included = facts.stream().filter(MeasurementFact::included)
            .max(Comparator.comparing(MeasurementFact::periodEnd,Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(MeasurementFact::periodStart,Comparator.nullsFirst(Comparator.naturalOrder())));
		if (included.isPresent()) {
			return Optional.of(new Unit(connector, week, UnitState.VALIDATED, included.get()));
		}
		if (!facts.isEmpty()) {
			return Optional.of(new Unit(connector, week, stateOf(facts), null));
		}
		if (!connector.connectorActive()) {
			return Optional.of(new Unit(connector, week, UnitState.RETIRED, null));
		}
		if (connector.equipmentStatus() != AdministrativeStatus.ACTIVE) {
			return Optional.empty();
		}
		return Optional.of(new Unit(connector, week, UnitState.WITHOUT_MEASUREMENT, null));
	}

	private static UnitState stateOf(List<MeasurementFact> facts) {
		if (facts.stream().anyMatch(MeasurementFact::excluded)) {
			return UnitState.EXCLUDED;
		}
		Set<ReadingClass> specials = facts.stream().filter(MeasurementFact::validated)
				.map(MeasurementFact::readingClass).collect(Collectors.toSet());
		if (specials.contains(ReadingClass.RETIRED)) {
			return UnitState.RETIRED;
		}
		if (specials.contains(ReadingClass.OUT_OF_SCOPE)) {
			return UnitState.OUT_OF_SCOPE;
		}
		if (specials.contains(ReadingClass.NO_DATA)) {
			return UnitState.NO_DATA;
		}
		return facts.stream().anyMatch(fact -> !fact.validated()) ? UnitState.PENDING_REVIEW
				: UnitState.WITHOUT_MEASUREMENT;
	}

	public static Kpis kpis(Collection<Unit> units) {
		List<MeasurementFact> included = included(units);
        if (included.stream().anyMatch(f->f.temporalPrecision()!=TemporalPrecision.KNOWN_RANGE))
            throw new IllegalArgumentException("El informe sin horario usa referenceKpis, no ponderacion.");
		long evaluated = sum(included, MeasurementFact::evaluatedMinutes);
		long offline = sum(included, MeasurementFact::offlineMinutes);
		long fault = sum(included, MeasurementFact::faultMinutes);
		Map<UUID, List<Unit>> byEquipment = units.stream()
				.collect(Collectors.groupingBy(unit -> unit.connector().equipmentId()));
		int evaluatedEquipment = (int) byEquipment.values().stream()
				.filter(list -> list.stream().anyMatch(unit -> unit.fact() != null)).count();
		int withoutData = (int) byEquipment.values().stream().filter(list -> list.stream()
				.noneMatch(unit -> unit.fact() != null)
				&& list.stream().anyMatch(UptimeCalculator::lacksData)).count();
		return new Kpis(UptimeFormula.percent(evaluated, down(included)), evaluated, hours(offline), hours(fault),
				evaluatedEquipment, withoutData, included.size(), denominator(units));
	}

	private static boolean lacksData(Unit unit) {
		return unit.state() == UnitState.NO_DATA || unit.state() == UnitState.WITHOUT_MEASUREMENT;
	}

	private static int denominator(Collection<Unit> units) {
		return (int) units.stream().filter(unit -> !OUTSIDE_DENOMINATOR.contains(unit.state())).count();
	}

	public static Coverage coverage(Collection<Unit> units) {
		Map<UnitState, Integer> byState = new EnumMap<>(UnitState.class);
		for (UnitState state : UnitState.values()) {
			byState.put(state, 0);
		}
		units.forEach(unit -> byState.merge(unit.state(), 1, Integer::sum));
		int included = included(units).size();
		int denominator = denominator(units);
		BigDecimal percent = denominator == 0 ? null
				: BigDecimal.valueOf(included).multiply(HUNDRED).divide(BigDecimal.valueOf(denominator), 2,
						RoundingMode.HALF_UP);
		return new Coverage(percent, included, denominator, byState);
	}

	/** Menor a mayor sobre el valor presentado; empates comparten posicion. Sin corte Top. */
	public static List<ConnectorResult> ranking(Collection<Unit> units) {
		Map<MeasurableConnector, List<MeasurementFact>> grouped = new LinkedHashMap<>();
		units.stream().filter(unit -> unit.fact() != null)
				.forEach(unit -> grouped.computeIfAbsent(unit.connector(), key -> new ArrayList<>()).add(unit.fact()));
		List<ConnectorResult> sorted = grouped.entrySet().stream().map(entry -> result(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(ConnectorResult::uptimePercent)
						.thenComparing(item -> item.connector().equipmentName())
						.thenComparingInt(item -> item.connector().ordinal()))
				.toList();
		List<ConnectorResult> ranked = new ArrayList<>(sorted.size());
		for (int index = 0; index < sorted.size(); index++) {
			ConnectorResult item = sorted.get(index);
			boolean tied = index > 0 && item.uptimePercent().compareTo(ranked.get(index - 1).uptimePercent()) == 0;
			int position = tied ? ranked.get(index - 1).position() : index + 1;
			ranked.add(new ConnectorResult(position, item.connector(), item.uptimePercent(), item.evaluatedMinutes(),
					item.offlineMinutes(), item.faultMinutes(), item.from(), item.to(),item.referenceWeek(),item.temporalPrecision(),item.aggregationMethod()));
		}
		return ranked;
	}

	private static ConnectorResult result(MeasurableConnector connector, List<MeasurementFact> facts) {
        if (!facts.isEmpty() && facts.getFirst().temporalPrecision()==TemporalPrecision.REFERENCE_WEEK) {
            StateComposition c=StateComposition.from(facts);
            return new ConnectorResult(0,connector,reportMean(facts),null,c.offlineMinutes(),c.faultMinutes(),
                null,null,facts.getFirst().referenceWeek(),TemporalPrecision.REFERENCE_WEEK,"REPORT_MEAN");
        }
		long evaluated = sum(facts, MeasurementFact::evaluatedMinutes);
		long offline = sum(facts, MeasurementFact::offlineMinutes);
		return new ConnectorResult(0, connector, UptimeFormula.percent(evaluated, down(facts)), evaluated, offline,
				sum(facts, MeasurementFact::faultMinutes),
				facts.stream().map(MeasurementFact::periodStart).min(Comparator.naturalOrder()).orElseThrow(),
				facts.stream().map(MeasurementFact::periodEnd).max(Comparator.naturalOrder()).orElseThrow());
	}

	/** Promedio de conectores ponderado por minutos, con cobertura en la unidad de la vista. */
	public static List<EquipmentResult> equipment(Collection<Unit> units) {
		Map<UUID, List<Unit>> grouped = units.stream().collect(Collectors.groupingBy(
				unit -> unit.connector().equipmentId(), LinkedHashMap::new, Collectors.toList()));
		return grouped.values().stream().map(UptimeCalculator::equipmentResult)
				.sorted(Comparator.comparing(item -> item.sample().equipmentName())).toList();
	}

	private static EquipmentResult equipmentResult(List<Unit> units) {
        List<MeasurementFact> reference = included(units);
        if (reference.stream().anyMatch(f->f.temporalPrecision()==TemporalPrecision.REFERENCE_WEEK)) {
            StateComposition c=StateComposition.from(reference);
            return new EquipmentResult(units.getFirst().connector(),reportMean(reference),null,
                c.offlineMinutes(),c.faultMinutes(),reference.size(),denominator(units),
                reference.getFirst().referenceWeek(),TemporalPrecision.REFERENCE_WEEK,"REPORT_MEAN");
        }

		List<MeasurementFact> included = included(units);
		long evaluated = sum(included, MeasurementFact::evaluatedMinutes);
		long offline = sum(included, MeasurementFact::offlineMinutes);
		return new EquipmentResult(units.getFirst().connector(), UptimeFormula.percent(evaluated, down(included)), evaluated,
				offline, sum(included, MeasurementFact::faultMinutes), included.size(), denominator(units));
	}

    public static List<EquipmentResult> referenceEquipment(Collection<Unit> units,LocalDate week) {
        var grouped=units.stream().collect(Collectors.groupingBy(u->u.connector().equipmentId(),
            LinkedHashMap::new,Collectors.toList()));
        return grouped.values().stream().map(list->{
            var facts=included(list);var c=StateComposition.from(facts);
            return new EquipmentResult(list.getFirst().connector(),reportMean(facts),null,c.offlineMinutes(),
                c.faultMinutes(),facts.size(),denominator(list),week,TemporalPrecision.REFERENCE_WEEK,"REPORT_MEAN");
        }).sorted(Comparator.comparing(r->r.sample().equipmentName())).toList();
    }

    public static ReferenceReportKpis referenceKpis(Collection<Unit> units,LocalDate referenceWeek) {
        var included=included(units);
        var grouped=units.stream().collect(Collectors.groupingBy(u->u.connector().equipmentId()));
        int evaluated=(int)grouped.values().stream().filter(list->list.stream().anyMatch(u->u.fact()!=null)).count();
        int missing=(int)grouped.values().stream().filter(list->list.stream().noneMatch(u->u.fact()!=null)
            &&list.stream().anyMatch(UptimeCalculator::lacksData)).count();
        return new ReferenceReportKpis(referenceWeek,reportMean(included),evaluated,missing,
            included.size(),denominator(units),coverage(units),StateComposition.from(included));
    }
    private static BigDecimal reportMean(Collection<MeasurementFact> facts) {
        if (facts.isEmpty()) return null;
        if (facts.stream().anyMatch(f->f.temporalPrecision()!=TemporalPrecision.REFERENCE_WEEK))
            throw new IllegalArgumentException("No mezclar hechos de distinto metodo.");
        return facts.stream().map(MeasurementFact::reportedPercent).reduce(BigDecimal.ZERO,BigDecimal::add)
            .divide(BigDecimal.valueOf(facts.size()),2,RoundingMode.HALF_UP);
    }
    public static StateComposition composition(Collection<Unit> units) {
        return StateComposition.from(included(units));
    }

	/** Un renglon por conector y estado; las excluidas solo para roles operativos. */
	public static List<Exclusion> exclusions(Collection<Unit> units, boolean operational) {
		return units.stream()
				.filter(unit -> LISTED.contains(unit.state()) || operational && unit.state() == UnitState.EXCLUDED)
				.map(unit -> new Exclusion(unit.connector(), unit.state())).distinct()
				.sorted(Comparator.comparing((Exclusion item) -> item.connector().equipmentName())
						.thenComparingInt(item -> item.connector().ordinal()))
				.toList();
	}

	private static List<MeasurementFact> included(Collection<Unit> units) {
		return units.stream().map(Unit::fact).filter(Objects::nonNull).toList();
	}

	private static BigDecimal down(Collection<MeasurementFact> facts) {
		return facts.stream().map(MeasurementFact::downMinutes).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static long sum(Collection<MeasurementFact> facts, ToLongFunction<MeasurementFact> value) {
		return facts.stream().mapToLong(value).sum();
	}

	private static BigDecimal hours(long minutes) {
		return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
	}
}

package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Judgment;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Measure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Result;

/**
 * Indicadores de gestion de incidencias (FR-080 a FR-086): el tablero, el PDF y el Excel salen de este calculo, asi que
 * coinciden. Funcion pura sobre los episodios cuyo T0 cae en el periodo, con los hitos tal como se conocian en el corte.
 *
 * <p>Los cerrados como «Duplicado» o «Registrado por error» se informan aparte y quedan fuera de los promedios, los
 * repartos y las referencias (FR-050). Cada promedio lleva su denominador y los episodios que no entran: «No aplica»,
 * «Sin dato» y los que todavia no tienen el hito (abiertos). Un hito sin hora nunca cuenta como cero (FR-042). Las
 * duraciones son minutos enteros hacia abajo y el promedio, con un decimal. No hay umbrales propios: las referencias
 * vienen ya evaluadas (FR-054) y los repetidores no llevan etiqueta (Protocolo §13).
 */
public final class IncidentKpiCalculator {

	/** Equipos repetidores que se listan. */
	public static final int TOP_REPEATERS = 10;
	/** El «% de validaciones ≤ 5 min» se informa con esa referencia. */
	public static final String VALIDATION_LABEL = "Validaciones dentro de la referencia";

	/** Un episodio del periodo, con lo que el calculo necesita. */
	public record EpisodeFact(UUID ticketId, String code, int ordinal, boolean open, ClosureResult closure,
			IncidentType type, ElementClass elementClass, String network, UUID equipmentId, String equipmentLabel,
			Priority priority, ReportOrigin origin, Instant t0, Map<MilestoneKind, Value> milestones,
			Long elapsedOpenMinutes, List<Judgment> judgments, String deviationJustification) {

		public boolean excluded() {
			return closure != null && closure.excludedFromIndicators();
		}
	}

	/** Una visita con las etapas que alcanzo, para contarlas por etapa alcanzada (P-08). */
	public record VisitFact(UUID episodeId, Set<VisitStage> reached) {
	}

	/** Una medida de tiempo: promedio, denominador y los que no entran (FR-080). */
	public record TimeMeasure(String pair, BigDecimal averageMinutes, int n, int notApplicable, int noData, int open) {
	}

	/** Conteos de una referencia por medida y tipo. */
	public record ReferenceCount(Measure measure, IncidentType type, int within, int outside, int notEvaluable) {
	}

	/** Un episodio fuera de referencia con su justificacion (FR-053). */
	public record Deviation(String code, int ordinal, IncidentType type, Measure measure, String justification) {
	}

	public record Summary(int uniqueTickets, int episodes, int openEpisodes, int closedEpisodes, int reopened,
			int critical, int excluded) {
	}

	public record VisitCounts(int requested, int confirmed, int cancelledOrPostponed, int executed) {
	}

	public record Repeater(String label, int episodes) {
	}

	public record Kpis(Summary summary, List<TimeMeasure> measures, int validationN, int validationWithin,
			List<ReferenceCount> references, List<IncidentType> withoutReference, List<Deviation> deviations,
			Map<String, Integer> byDay, Map<String, Integer> byNetwork, Map<String, Integer> byType,
			Map<String, Integer> byPriority, Map<String, Integer> byClass, Map<String, Integer> byEquipment,
			Map<String, Integer> byOrigin, VisitCounts visits, List<Repeater> repeaters) {

		/** El porcentaje de validaciones dentro de la referencia, nulo si no hay episodios que medir. */
		public BigDecimal validationPercent() {
			return validationN == 0 ? null
					: BigDecimal.valueOf(validationWithin * 100L).divide(BigDecimal.valueOf(validationN), 1,
							RoundingMode.HALF_UP);
		}
	}

	private static final MilestoneKind[][] PAIRS = { { MilestoneKind.T1, MilestoneKind.T0 },
			{ MilestoneKind.T2, MilestoneKind.T0 }, { MilestoneKind.T3, MilestoneKind.T0 },
			{ MilestoneKind.T4, MilestoneKind.T0 }, { MilestoneKind.T4, MilestoneKind.T3 } };

	private IncidentKpiCalculator() {
	}

	/** Los nombres de las cinco medidas, en orden: «T1-T0», «T2-T0», «T3-T0», «T4-T0» y «T4-T3». */
	public static List<String> pairNames() {
		return java.util.Arrays.stream(PAIRS).map(pair -> pair[0] + "-" + pair[1]).toList();
	}

	/** Minutos enteros hacia abajo entre dos hitos conocidos; nulo si falta alguno (FR-042). */
	public static Integer minutes(Map<MilestoneKind, Value> milestones, MilestoneKind end, MilestoneKind start) {
		Instant to = timeOf(milestones, end);
		Instant from = timeOf(milestones, start);
		return to == null || from == null ? null : (int) Math.floorDiv(Duration.between(from, to).getSeconds(), 60);
	}

	public static Instant timeOf(Map<MilestoneKind, Value> milestones, MilestoneKind kind) {
		Value value = milestones.get(kind);
		return value != null && value.status() == MilestoneStatus.RECORDED ? value.occurredAt() : null;
	}

	public static Kpis compute(List<EpisodeFact> facts, List<VisitFact> visits, ZoneId zone) {
		List<EpisodeFact> counted = facts.stream().filter(fact -> !fact.excluded()).toList();
		Set<UUID> ticketIds = new HashSet<>();
		facts.forEach(fact -> ticketIds.add(fact.ticketId()));
		Summary summary = new Summary(ticketIds.size(), facts.size(), (int) counted.stream().filter(EpisodeFact::open).count(),
				(int) counted.stream().filter(fact -> !fact.open()).count(),
				(int) counted.stream().filter(fact -> fact.ordinal() > 1).count(),
				(int) counted.stream().filter(fact -> fact.priority() == Priority.CRITICAL).count(),
				facts.size() - counted.size());

		return new Kpis(summary, measures(counted), validationN(counted), validationWithin(counted), references(counted),
				withoutReference(counted), deviations(counted), byDay(counted, zone),
				distribution(counted, fact -> fact.network() == null ? "Sin red" : fact.network()),
				distribution(counted, fact -> fact.type().name()), distribution(counted, fact -> fact.priority().name()),
				distribution(counted, fact -> fact.elementClass().name()),
				distribution(counted, fact -> fact.equipmentLabel() == null ? "Sin equipo" : fact.equipmentLabel()),
				distribution(counted, fact -> fact.origin().name()), visitCounts(visits),
				repeaters(counted));
	}

	private static List<TimeMeasure> measures(List<EpisodeFact> counted) {
		List<TimeMeasure> result = new ArrayList<>();
		for (MilestoneKind[] pair : PAIRS) {
			long sum = 0;
			int n = 0;
			int notApplicable = 0;
			int noData = 0;
			int open = 0;
			for (EpisodeFact fact : counted) {
				MilestoneStatus end = status(fact, pair[0]);
				MilestoneStatus start = status(fact, pair[1]);
				if (end == MilestoneStatus.RECORDED && start == MilestoneStatus.RECORDED) {
					sum += minutes(fact.milestones(), pair[0], pair[1]);
					n++;
				}
				else if (end == MilestoneStatus.NOT_APPLICABLE || start == MilestoneStatus.NOT_APPLICABLE) {
					notApplicable++;
				}
				else if (end == MilestoneStatus.NO_DATA || start == MilestoneStatus.NO_DATA) {
					noData++;
				}
				else {
					open++;
				}
			}
			BigDecimal average = n == 0 ? null
					: BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
			result.add(new TimeMeasure(pair[0] + "-" + pair[1], average, n, notApplicable, noData, open));
		}
		return result;
	}

	private static MilestoneStatus status(EpisodeFact fact, MilestoneKind kind) {
		Value value = fact.milestones().get(kind);
		return value == null ? MilestoneStatus.PENDING : value.status();
	}

	private static boolean hasValidation(EpisodeFact fact) {
		return fact.judgments().stream().anyMatch(judgment -> judgment.measure() == Measure.VALIDATION
				&& (judgment.result() == Result.WITHIN || judgment.result() == Result.OUTSIDE));
	}

	private static int validationN(List<EpisodeFact> counted) {
		return (int) counted.stream().filter(IncidentKpiCalculator::hasValidation).count();
	}

	private static int validationWithin(List<EpisodeFact> counted) {
		return (int) counted.stream().filter(fact -> fact.judgments().stream()
				.anyMatch(judgment -> judgment.measure() == Measure.VALIDATION && judgment.result() == Result.WITHIN)).count();
	}

	private static List<ReferenceCount> references(List<EpisodeFact> counted) {
		Map<String, int[]> counts = new TreeMap<>();
		Map<String, ReferenceCount> keys = new HashMap<>();
		for (EpisodeFact fact : counted) {
			for (Judgment judgment : fact.judgments()) {
				if (judgment.result() == Result.NO_REFERENCE) {
					continue;
				}
				String key = judgment.measure() + "|" + fact.type();
				int[] row = counts.computeIfAbsent(key, ignored -> new int[3]);
				row[judgment.result() == Result.WITHIN ? 0 : judgment.result() == Result.OUTSIDE ? 1 : 2]++;
				keys.putIfAbsent(key, new ReferenceCount(judgment.measure(), fact.type(), 0, 0, 0));
			}
		}
		List<ReferenceCount> result = new ArrayList<>();
		counts.forEach((key, row) -> result.add(new ReferenceCount(keys.get(key).measure(), keys.get(key).type(), row[0],
				row[1], row[2])));
		return result;
	}

	/** Los tipos del periodo que no tienen ninguna referencia: se muestran sin juicio (FR-054). */
	private static List<IncidentType> withoutReference(List<EpisodeFact> counted) {
		Set<IncidentType> with = new HashSet<>();
		Set<IncidentType> all = new java.util.TreeSet<>();
		for (EpisodeFact fact : counted) {
			all.add(fact.type());
			if (!fact.judgments().isEmpty()) {
				with.add(fact.type());
			}
		}
		all.removeAll(with);
		return new ArrayList<>(all);
	}

	private static List<Deviation> deviations(List<EpisodeFact> counted) {
		List<Deviation> result = new ArrayList<>();
		for (EpisodeFact fact : counted) {
			for (Judgment judgment : fact.judgments()) {
				if (judgment.result() == Result.OUTSIDE) {
					result.add(new Deviation(fact.code(), fact.ordinal(), fact.type(), judgment.measure(),
							fact.deviationJustification()));
				}
			}
		}
		result.sort(Comparator.comparing(Deviation::code).thenComparingInt(Deviation::ordinal)
				.thenComparing(Deviation::measure));
		return result;
	}

	/** Episodios por dia local de su T0, en orden de fecha. */
	private static Map<String, Integer> byDay(List<EpisodeFact> counted, ZoneId zone) {
		Map<String, Integer> days = new TreeMap<>();
		counted.forEach(fact -> days.merge(fact.t0().atZone(zone).toLocalDate().toString(), 1, Integer::sum));
		return days;
	}

	/** Conteo por clave, de mayor a menor y luego alfabetico, para que el orden sea estable. */
	private static Map<String, Integer> distribution(List<EpisodeFact> counted, Function<EpisodeFact, String> key) {
		Map<String, Integer> counts = new HashMap<>();
		counted.forEach(fact -> counts.merge(key.apply(fact), 1, Integer::sum));
		Map<String, Integer> sorted = new LinkedHashMap<>();
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
		return sorted;
	}

	/** Visitas por etapa alcanzada; ya llegan filtradas a los episodios contados. */
	private static VisitCounts visitCounts(List<VisitFact> visits) {
		int requested = 0;
		int confirmed = 0;
		int cancelledOrPostponed = 0;
		int executed = 0;
		for (VisitFact visit : visits) {
			requested++;
			if (visit.reached().contains(VisitStage.CONFIRMED)) {
				confirmed++;
			}
			if (visit.reached().contains(VisitStage.CANCELLED) || visit.reached().contains(VisitStage.POSTPONED)) {
				cancelledOrPostponed++;
			}
			if (visit.reached().contains(VisitStage.EXECUTED)) {
				executed++;
			}
		}
		return new VisitCounts(requested, confirmed, cancelledOrPostponed, executed);
	}

	/** Equipos por numero de episodios en el periodo, sin etiqueta de «recurrente» (Protocolo §13). */
	private static List<Repeater> repeaters(List<EpisodeFact> counted) {
		Map<UUID, Integer> counts = new HashMap<>();
		Map<UUID, String> labels = new HashMap<>();
		for (EpisodeFact fact : counted) {
			if (fact.equipmentId() != null) {
				counts.merge(fact.equipmentId(), 1, Integer::sum);
				labels.putIfAbsent(fact.equipmentId(), fact.equipmentLabel());
			}
		}
		return counts.entrySet().stream()
				.map(entry -> new Repeater(labels.get(entry.getKey()), entry.getValue()))
				.sorted(Comparator.comparingInt(Repeater::episodes).reversed().thenComparing(Repeater::label))
				.limit(TOP_REPEATERS).toList();
	}

	/** Las etapas que alcanzo una visita: toda visita se solicito, y el resto sale de sus eventos de etapa. */
	public static Set<VisitStage> reached(List<VisitStage> stages) {
		Set<VisitStage> reached = new java.util.LinkedHashSet<>(stages);
		reached.add(VisitStage.REQUESTED);
		return reached;
	}
}

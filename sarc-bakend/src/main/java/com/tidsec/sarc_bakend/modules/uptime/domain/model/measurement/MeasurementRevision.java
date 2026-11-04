package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFormula;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Valores de una medicion en un momento. Solo cambian estado, validacion y fin de vigencia. */
@Entity
@Table(name = "upt_measurement_revision")
public class MeasurementRevision {

	/** Campos de {@code source_values} que guardan cada estado tal como llego. */
	private static final List<String> RAW_MINUTES = List.of("availableMinutes", "occupiedMinutes", "faultMinutes",
			"offlineMinutes");

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID measurementId;

	@Column(nullable = false, updatable = false)
	private int revision;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ReadingClass readingClass;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private PeriodType periodType;

	@Column(updatable = false)
	private Integer availableMinutes;

	@Column(updatable = false)
	private Integer occupiedMinutes;

	@Column(updatable = false)
	private Integer faultMinutes;

	@Column(updatable = false)
	private Integer offlineMinutes;

	@Column(updatable = false, precision = 12, scale = 4)
	private BigDecimal reportedPercent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private UptimeBasis uptimeBasis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReviewState reviewState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private List<Observation> observations;

	@Column(updatable = false, length = 500)
	private String justification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private MeasurementOrigin origin;

	@Column(updatable = false)
	private UUID importBatchId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> sourceValues;

	@Column(nullable = false, updatable = false, length = 150)
	private String equipmentLabel;

	@Column(updatable = false, length = 80)
	private String equipmentCode;

	@Column(nullable = false, updatable = false, length = 100)
	private String connectorTypeLabel;

	@Column(updatable = false)
	private UUID exactConfirmedBy;

	@Column(updatable = false)
	private Instant exactConfirmedAt;

	@Column(updatable = false)
	private UUID enteredBy;

	@Column(nullable = false, updatable = false)
	private Instant enteredAt;

	private UUID validatedBy;

	private Instant validatedAt;

	@Column(length = 500)
	private String validationJustification;

	private Instant supersededAt;

	protected MeasurementRevision() {
	}

	MeasurementRevision(UUID measurementId, int revision, RevisionData data) {
		this.id = UUID.randomUUID();
		this.measurementId = measurementId;
		this.revision = revision;
		this.readingClass = data.readingClass();
		this.periodType = data.periodType();
		this.availableMinutes = data.availableMinutes();
		this.occupiedMinutes = data.occupiedMinutes();
		this.faultMinutes = data.faultMinutes();
		this.offlineMinutes = data.offlineMinutes();
		this.reportedPercent = data.reportedPercent();
		this.uptimeBasis = data.uptimeBasis();
		this.reviewState = ReviewState.PENDING;
		this.observations = List.copyOf(data.observations());
		this.justification = data.justification();
		this.origin = data.origin();
		this.importBatchId = data.importBatchId();
		this.sourceValues = data.sourceValues();
		this.equipmentLabel = data.equipmentLabel();
		this.equipmentCode = data.equipmentCode();
		this.connectorTypeLabel = data.connectorTypeLabel();
		this.exactConfirmedBy = data.exactConfirmedBy();
		this.exactConfirmedAt = data.exactConfirmedAt();
		this.enteredBy = data.enteredBy();
		this.enteredAt = data.enteredAt();
	}

	void validate(UUID actor, Instant at, String validationJustification) {
		this.reviewState = ReviewState.VALIDATED;
		this.validatedBy = actor;
		this.validatedAt = at;
		this.validationJustification = validationJustification;
	}

	void supersede(Instant at) {
		this.reviewState = ReviewState.SUPERSEDED;
		this.supersededAt = at;
	}

	/** Misma lectura: conserva los originales que no cupieron en minutos (FR-020). */
	public boolean sameReading(RevisionData other) {
		return readingClass == other.readingClass() && periodType == other.periodType()
				&& uptimeBasis == other.uptimeBasis() && Objects.equals(availableMinutes, other.availableMinutes())
				&& Objects.equals(occupiedMinutes, other.occupiedMinutes())
				&& Objects.equals(faultMinutes, other.faultMinutes())
				&& Objects.equals(offlineMinutes, other.offlineMinutes()) && samePercent(other.reportedPercent())
				&& sameOriginals(other.sourceValues());
	}

	private boolean samePercent(BigDecimal other) {
		return reportedPercent == null ? other == null : other != null && reportedPercent.compareTo(other) == 0;
	}

	private boolean sameOriginals(Map<String, Object> other) {
		if (!hasMissingMinutes()) {
			return true;
		}
		return RAW_MINUTES.stream().allMatch(key -> sameNumber(raw(sourceValues, key), raw(other, key)));
	}

	private boolean hasMissingMinutes() {
		return readingClass == ReadingClass.MEASURED && (availableMinutes == null || faultMinutes == null
				|| offlineMinutes == null || (uptimeBasis == UptimeBasis.STATES && occupiedMinutes == null));
	}

	private static Object raw(Map<String, Object> values, String key) {
		return values == null ? null : values.get(key);
	}

	private static boolean sameNumber(Object left, Object right) {
		if (left == null || right == null) {
			return left == right;
		}
		try {
			return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
		}
		catch (NumberFormatException notNumeric) {
			return left.toString().equals(right.toString());
		}
	}

	/** Tiempo evaluado, o null si la lectura no tiene los cuatro estados (tampoco el uptime informado). */
	public Long evaluatedMinutes() {
		if (hasMissingMinutes() || readingClass != ReadingClass.MEASURED || uptimeBasis != UptimeBasis.STATES) {
			return null;
		}
		return (long) availableMinutes + occupiedMinutes + faultMinutes + offlineMinutes;
	}

	public boolean isValidatable() {
		return observations.stream().allMatch(Observation::validatable);
	}

	/** Porcentaje presentable; las lecturas que deben corregirse no lo tienen. */
	public BigDecimal uptimePercent() {
		if (uptimeBasis == UptimeBasis.REPORTED_PERCENT) {
			return hasMissingMinutes() || !isValidatable() ? null : reportedPercent.setScale(2, RoundingMode.HALF_UP);
		}
		Long evaluated = evaluatedMinutes();
		return evaluated == null || !isValidatable() ? null : UptimeFormula.percent(evaluated, offlineMinutes);
	}

	public UUID getId() {
		return id;
	}

	public UUID getMeasurementId() {
		return measurementId;
	}

	public int getRevision() {
		return revision;
	}

	public ReadingClass getReadingClass() {
		return readingClass;
	}

	public PeriodType getPeriodType() {
		return periodType;
	}

	public Integer getAvailableMinutes() {
		return availableMinutes;
	}

	public Integer getOccupiedMinutes() {
		return occupiedMinutes;
	}

	public Integer getFaultMinutes() {
		return faultMinutes;
	}

	public Integer getOfflineMinutes() {
		return offlineMinutes;
	}

	public BigDecimal getReportedPercent() {
		return reportedPercent;
	}

	public UptimeBasis getUptimeBasis() {
		return uptimeBasis;
	}

	public ReviewState getReviewState() {
		return reviewState;
	}

	public List<Observation> getObservations() {
		return observations;
	}

	public String getJustification() {
		return justification;
	}

	public MeasurementOrigin getOrigin() {
		return origin;
	}

	public UUID getImportBatchId() {
		return importBatchId;
	}

	public Map<String, Object> getSourceValues() {
		return sourceValues;
	}

	public String getEquipmentLabel() {
		return equipmentLabel;
	}

	public String getEquipmentCode() {
		return equipmentCode;
	}

	public String getConnectorTypeLabel() {
		return connectorTypeLabel;
	}

	public UUID getExactConfirmedBy() {
		return exactConfirmedBy;
	}

	public Instant getExactConfirmedAt() {
		return exactConfirmedAt;
	}

	public UUID getEnteredBy() {
		return enteredBy;
	}

	public Instant getEnteredAt() {
		return enteredAt;
	}

	public UUID getValidatedBy() {
		return validatedBy;
	}

	public Instant getValidatedAt() {
		return validatedAt;
	}

	public String getValidationJustification() {
		return validationJustification;
	}

	public Instant getSupersededAt() {
		return supersededAt;
	}
}

package com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.policy.EquipmentClassification;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

/**
 * Equipo fisico del inventario maestro (RF-002): punto de carga o electrolinera. Su id es la
 * identidad estable que usan los demas modulos aunque cambie el nombre o se corrija la serie.
 * Red y tipo son inmutables; un equipo fisico distinto se registra como nuevo y apunta al que
 * reemplaza. Nunca se borra fisicamente.
 */
@Entity
@Table(name = "asset_equipment")
public class Equipment extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "EQUIPMENT";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ChargingNetwork network;

	@Enumerated(EnumType.STRING)
	@Column(name = "infrastructure_type", nullable = false, updatable = false, length = 20)
	private InfrastructureType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EquipmentBrand brand;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 80)
	private String serial;

	@Column(length = 80)
	private String assignedCode;

	@Column(length = 80)
	private String uidPuk;

	@Column(length = 300)
	private String location;

	@Column(length = 500)
	private String mapsUrl;

	@Column(length = 500)
	private String ocppUrl;

	@Column(length = 255)
	private String imageRef;

	@Column(nullable = false)
	private AdministrativeStatus adminStatus;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private UsageStatus usageStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ScheduleMode scheduleMode;

	@Column(updatable = false)
	private UUID replacesEquipmentId;

	@OneToMany(mappedBy = "equipment", cascade = CascadeType.ALL)
	@OrderBy("ordinal")
	private List<Connector> connectors = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "asset_schedule_interval", joinColumns = @JoinColumn(name = "equipment_id"))
	private List<WeeklyInterval> weeklyIntervals = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "asset_schedule_exception", joinColumns = @JoinColumn(name = "equipment_id"))
	private List<ScheduleException> scheduleExceptions = new ArrayList<>();

	protected Equipment() {
	}

	private Equipment(ChargingNetwork network, InfrastructureType type, UUID replacesEquipmentId) {
		super(UUID.randomUUID());
		this.network = network;
		this.type = type;
		this.adminStatus = AdministrativeStatus.ACTIVE;
		this.scheduleMode = ScheduleMode.NOT_DEFINED;
		this.replacesEquipmentId = replacesEquipmentId;
	}

	/**
	 * Alta de un equipo. {@code replaces} es el equipo fisico al que sustituye, si lo hay: debe
	 * estar fuera de servicio (inactivo o eliminado).
	 */
	public static Equipment register(ChargingNetwork network, InfrastructureType type, EquipmentDetails details,
			List<ConnectorType> connectorTypes, Equipment replaces) {
		if (replaces != null && replaces.adminStatus == AdministrativeStatus.ACTIVE) {
			throw new BusinessRuleException("REPLACED_EQUIPMENT_ACTIVE",
					"El equipo reemplazado debe estar inactivo o eliminado antes del reemplazo");
		}
		Equipment equipment = withDetails(network, type, details, replaces == null ? null : replaces.getId(), null);
		equipment.replaceConnectors(connectorTypes);
		return equipment;
	}

	/**
	 * Borrador sin persistir con los datos dados, para revisar una carga masiva: aplica las mismas
	 * reglas que el alta y la edicion. Si {@code current} es el equipo que se editaria, sus tipos de
	 * conector se aceptan en la misma posicion aunque hoy esten inactivos, como al editarlo.
	 */
	public static Equipment draft(ChargingNetwork network, InfrastructureType type, EquipmentDetails details,
			List<ConnectorType> connectorTypes, Equipment current) {
		Equipment draft = withDetails(network, type, details, null, current == null ? null : current.assignedCode);
		if (current != null) {
			current.activeConnectors().forEach(connector -> draft.connectors
					.add(new Connector(draft, connector.getOrdinal(), connector.getConnectorType())));
		}
		draft.replaceConnectors(connectorTypes);
		return draft;
	}

	private static Equipment withDetails(ChargingNetwork network, InfrastructureType type, EquipmentDetails details,
			UUID replacesEquipmentId, String retainedCode) {
		if (network == null || type == null) {
			throw new BusinessRuleException("INVALID_CLASSIFICATION", "La red y el tipo de equipo son obligatorios");
		}
		Equipment equipment = new Equipment(network, type, replacesEquipmentId);
		equipment.assignedCode = retainedCode;
		equipment.applyDetails(details);
		return equipment;
	}

	public EquipmentClassification classification() {
		return new EquipmentClassification(network, type);
	}

	/** Actualiza los datos editables. Corregir la serie mantiene la identidad del equipo. */
	public void applyDetails(EquipmentDetails details) {
		EquipmentClassification classification = classification();
		classification.requireUsageStatus(details.usageStatus());
		this.brand = require(details.brand(), "La marca es obligatoria");
		this.name = requireText(details.name(), 150, "El nombre es obligatorio (maximo 150)");
		this.serial = requireText(normalizeIdentifier(details.serial()), 80, "La serie es obligatoria (maximo 80)");
		String requestedCode = details.requestedCode();
		if (!classification.usesSerialAsCode() && (requestedCode == null || requestedCode.isBlank())) {
			requestedCode = this.assignedCode;
		}
		this.assignedCode = classification.resolveAssignedCode(serial, requestedCode, details.usageStatus());
		this.uidPuk = optionalText(normalizeIdentifier(details.uidPuk()), 80, "El UID/PUK admite 80 caracteres");
		classification.requireUidPuk(uidPuk);
		this.location = optionalText(details.location(), 300, "La ubicacion admite 300 caracteres");
		this.mapsUrl = optionalUrl(details.mapsUrl(), Set.of("https://"), "El enlace de mapas debe usar https");
		this.ocppUrl = optionalUrl(details.ocppUrl(), Set.of("ws://", "wss://", "http://", "https://"),
				"La URL OCPP debe usar ws, wss, http o https");
		this.usageStatus = details.usageStatus();
	}

	/**
	 * Define los conectores en orden de posicion. Los existentes conservan su id; los que sobran
	 * quedan inactivos. Un tipo de conector inactivo solo puede seguir en la posicion que ya tenia.
	 */
	public void replaceConnectors(List<ConnectorType> connectorTypes) {
		classification().requireConnectorCount(connectorTypes.size());
		for (int index = 0; index < connectorTypes.size(); index++) {
			int ordinal = index + 1;
			ConnectorType connectorType = connectorTypes.get(index);
			Optional<Connector> existing = connectorAt(ordinal);
			boolean unchanged = existing.map(c -> c.getConnectorType().getId().equals(connectorType.getId()))
					.orElse(false);
			if (!connectorType.isActive() && !unchanged) {
				throw new BusinessRuleException("CONNECTOR_TYPE_INACTIVE",
						"El tipo de conector " + connectorType.getCode() + " esta inactivo");
			}
			existing.ifPresentOrElse(connector -> connector.assign(connectorType),
					() -> connectors.add(new Connector(this, ordinal, connectorType)));
		}
		connectors.stream().filter(c -> c.getOrdinal() > connectorTypes.size()).forEach(Connector::retire);
	}

	public void changeStatus(AdministrativeStatus status) {
		this.adminStatus = require(status, "El estado administrativo es obligatorio");
	}

	public boolean isDeleted() {
		return adminStatus == AdministrativeStatus.DELETED;
	}

	/** Define el horario completo. Los intervalos de la semana no pueden solaparse entre si. */
	public void defineSchedule(ScheduleMode mode, List<WeeklyInterval> intervals, List<ScheduleException> exceptions) {
		require(mode, "El modo de horario es obligatorio");
		if (mode == ScheduleMode.WEEKLY && intervals.isEmpty()) {
			throw new BusinessRuleException("SCHEDULE_INTERVALS_REQUIRED",
					"Un horario semanal necesita al menos un intervalo");
		}
		if (mode != ScheduleMode.WEEKLY && !intervals.isEmpty()) {
			throw new BusinessRuleException("SCHEDULE_INTERVALS_NOT_ALLOWED",
					"Solo el horario semanal admite intervalos");
		}
		for (int i = 0; i < intervals.size(); i++) {
			for (int j = i + 1; j < intervals.size(); j++) {
				if (intervals.get(i).overlaps(intervals.get(j))) {
					throw new BusinessRuleException("SCHEDULE_INTERVALS_OVERLAP",
							"Los intervalos del horario no pueden solaparse");
				}
			}
		}
		Set<LocalDate> dates = new HashSet<>();
		if (!exceptions.stream().map(ScheduleException::date).allMatch(dates::add)) {
			throw new BusinessRuleException("SCHEDULE_EXCEPTION_DUPLICATED", "Solo una excepcion por fecha");
		}
		this.scheduleMode = mode;
		this.weeklyIntervals.clear();
		this.weeklyIntervals.addAll(intervals.stream()
				.sorted(Comparator.comparing(WeeklyInterval::dayOfWeek).thenComparing(WeeklyInterval::opensAt))
				.toList());
		this.scheduleExceptions.clear();
		this.scheduleExceptions.addAll(exceptions.stream().sorted(Comparator.comparing(ScheduleException::date))
				.toList());
	}

	/** Asocia una imagen y devuelve la referencia anterior, si habia, para descartarla. */
	public Optional<String> attachImage(String reference) {
		Optional<String> previous = Optional.ofNullable(imageRef);
		this.imageRef = reference;
		return previous;
	}

	public Optional<String> removeImage() {
		return attachImage(null);
	}

	public List<Connector> activeConnectors() {
		return connectors.stream().filter(Connector::isActive).toList();
	}

	private Optional<Connector> connectorAt(int ordinal) {
		return connectors.stream().filter(c -> c.getOrdinal() == ordinal).findFirst();
	}

	/** Series y UID/PUK se comparan sin espacios y en mayusculas para evitar duplicados por formato. */
	public static String normalizeIdentifier(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return raw.strip().toUpperCase(Locale.ROOT);
	}

	private static <T> T require(T value, String message) {
		if (value == null) {
			throw new BusinessRuleException("INVALID_EQUIPMENT", message);
		}
		return value;
	}

	private static String requireText(String value, int max, String message) {
		String text = optionalText(value, max, message);
		if (text == null) {
			throw new BusinessRuleException("INVALID_EQUIPMENT", message);
		}
		return text;
	}

	private static String optionalText(String value, int max, String message) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		if (text.length() > max) {
			throw new BusinessRuleException("INVALID_EQUIPMENT", message);
		}
		return text;
	}

	private static String optionalUrl(String value, Set<String> schemes, String message) {
		String url = optionalText(value, 500, message);
		if (url != null && schemes.stream().noneMatch(scheme -> url.toLowerCase(Locale.ROOT).startsWith(scheme))) {
			throw new BusinessRuleException("INVALID_EQUIPMENT", message);
		}
		return url;
	}

	public ChargingNetwork getNetwork() {
		return network;
	}

	public InfrastructureType getType() {
		return type;
	}

	public EquipmentBrand getBrand() {
		return brand;
	}

	public String getName() {
		return name;
	}

	public String getSerial() {
		return serial;
	}

	public String getAssignedCode() {
		return assignedCode;
	}

	public String getUidPuk() {
		return uidPuk;
	}

	public String getLocation() {
		return location;
	}

	public String getMapsUrl() {
		return mapsUrl;
	}

	public String getOcppUrl() {
		return ocppUrl;
	}

	public String getImageRef() {
		return imageRef;
	}

	public AdministrativeStatus getAdminStatus() {
		return adminStatus;
	}

	public UsageStatus getUsageStatus() {
		return usageStatus;
	}

	public ScheduleMode getScheduleMode() {
		return scheduleMode;
	}

	public UUID getReplacesEquipmentId() {
		return replacesEquipmentId;
	}

	public List<WeeklyInterval> getWeeklyIntervals() {
		return List.copyOf(weeklyIntervals);
	}

	public List<ScheduleException> getScheduleExceptions() {
		return List.copyOf(scheduleExceptions);
	}
}

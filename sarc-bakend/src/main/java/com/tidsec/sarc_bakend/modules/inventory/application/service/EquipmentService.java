package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.inventory.application.command.ChangeEquipmentStatusCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.command.DefineScheduleCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.command.RegisterEquipmentCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.command.UpdateEquipmentCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorTypeRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.persistence.AfterCommit;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.shared.storage.ImageFormat;

/** Casos de uso de escritura del inventario (RF-002). Solo administradores y analistas. */
@Service
@Transactional
@PreAuthorize(InventoryPermissions.EDITORS)
public class EquipmentService {

	private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);
	/** TRD 16.1: imagenes de hasta 10 MiB. */
	static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

	private final EquipmentRepository equipment;
	private final ConnectorTypeRepository connectorTypes;
	private final FileStorage fileStorage;
	private final EquipmentViewFactory views;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;

	EquipmentService(EquipmentRepository equipment, ConnectorTypeRepository connectorTypes, FileStorage fileStorage,
			EquipmentViewFactory views, AuditTrail audit, TransactionTemplate transactions) {
		this.equipment = equipment;
		this.connectorTypes = connectorTypes;
		this.fileStorage = fileStorage;
		this.views = views;
		this.audit = audit;
		this.transactions = transactions;
	}

	public EquipmentView register(RegisterEquipmentCommand command) {
		Equipment replaced = command.replacesEquipmentId() == null ? null : load(command.replacesEquipmentId());
		Equipment created = Equipment.register(command.network(), command.type(), command.details(),
				resolveConnectorTypes(command.connectorTypeIds()), replaced);
		requireUniqueIdentifiers(created);
		equipment.save(created);
		Map<String, String> details = new HashMap<>(Map.of("network", created.getNetwork().name(), "type",
				created.getType().name(), "serial", created.getSerial(), "assignedCode", created.getAssignedCode()));
		if (replaced != null) {
			details.put("replaces", replaced.getId().toString());
		}
		audit.record("EQUIPMENT_REGISTERED", Equipment.AUDIT_ENTITY_TYPE, created.getId(), details);
		return views.forCurrentUser(created);
	}

	public EquipmentView update(UpdateEquipmentCommand command) {
		Equipment target = loadForChange(command.id(), command.version());
		String previousSerial = target.getSerial();
		target.applyDetails(command.details());
		target.replaceConnectors(resolveConnectorTypes(command.connectorTypeIds()));
		requireUniqueIdentifiers(target);
		equipment.save(target);
		Map<String, String> details = new HashMap<>();
		if (!previousSerial.equals(target.getSerial())) {
			details.put("serialFrom", previousSerial);
			details.put("serialTo", target.getSerial());
		}
		audit.record(details.isEmpty() ? "EQUIPMENT_UPDATED" : "EQUIPMENT_SERIAL_CORRECTED",
				Equipment.AUDIT_ENTITY_TYPE, target.getId(), details);
		return views.forCurrentUser(target);
	}

	/** Activar, inactivar, eliminar logicamente o recuperar: el equipo y su historico se conservan. */
	public EquipmentView changeStatus(ChangeEquipmentStatusCommand command) {
		Equipment target = loadForChange(command.id(), command.version());
		String previous = target.getAdminStatus().name();
		target.changeStatus(command.status());
		equipment.save(target);
		audit.record("EQUIPMENT_STATUS_CHANGED", Equipment.AUDIT_ENTITY_TYPE, target.getId(),
				Map.of("from", previous, "to", target.getAdminStatus().name()));
		return views.forCurrentUser(target);
	}

	public EquipmentView defineSchedule(DefineScheduleCommand command) {
		Equipment target = loadForChange(command.equipmentId(), command.version());
		target.defineSchedule(command.mode(), command.intervals(), command.exceptions());
		equipment.save(target);
		audit.record("EQUIPMENT_SCHEDULE_DEFINED", Equipment.AUDIT_ENTITY_TYPE, target.getId(),
				Map.of("mode", command.mode().name()));
		return views.forCurrentUser(target);
	}

	/**
	 * Reemplaza la imagen del equipo. La subida al almacenamiento ocurre fuera de la transaccion
	 * para no retener una conexion de base de datos durante la transferencia; si luego no se puede
	 * guardar el cambio, la imagen recien subida se descarta. La anterior se borra al confirmar.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public EquipmentView attachImage(UUID id, long version, byte[] content) {
		if (content.length == 0 || content.length > MAX_IMAGE_BYTES) {
			throw new BusinessRuleException("INVALID_IMAGE", "La imagen debe pesar entre 1 byte y 10 MiB");
		}
		ImageFormat format = ImageFormat.detect(content).orElseThrow(
				() -> new BusinessRuleException("INVALID_IMAGE", "La imagen debe ser JPEG, PNG o WebP"));
		transactions.executeWithoutResult(status -> loadForChange(id, version));
		String reference = fileStorage.store("equipment/" + id, content);
		try {
			return transactions.execute(status -> {
				Equipment target = loadForChange(id, version);
				target.attachImage(reference).ifPresent(this::discardAfterCommit);
				equipment.save(target);
				audit.record("EQUIPMENT_IMAGE_CHANGED", Equipment.AUDIT_ENTITY_TYPE, id,
						Map.of("format", format.name()));
				return views.forCurrentUser(target);
			});
		}
		catch (RuntimeException ex) {
			discardAfterCommit(reference);
			throw ex;
		}
	}

	public EquipmentView removeImage(UUID id, long version) {
		Equipment target = loadForChange(id, version);
		target.removeImage().ifPresent(reference -> {
			discardAfterCommit(reference);
			audit.record("EQUIPMENT_IMAGE_REMOVED", Equipment.AUDIT_ENTITY_TYPE, id, Map.of());
		});
		equipment.save(target);
		return views.forCurrentUser(target);
	}

	/**
	 * Serie, codigo y UID/PUK son unicos en todo el inventario, eliminados incluidos (RF-002.2/3).
	 * Si el dueno es un equipo eliminado se indica su id para recuperarlo en vez de duplicarlo; si
	 * los identificadores pertenecen a equipos distintos se detiene sin fusionar identidades.
	 */
	private void requireUniqueIdentifiers(Equipment candidate) {
		List<Equipment> holders = equipment
				.findHoldersOf(candidate.getSerial(), candidate.getAssignedCode(), candidate.getUidPuk()).stream()
				.filter(holder -> !holder.getId().equals(candidate.getId()))
				.toList();
		if (holders.isEmpty()) {
			return;
		}
		if (holders.size() > 1) {
			throw new ConflictException("IDENTIFIERS_BELONG_TO_DIFFERENT_EQUIPMENT",
					"La serie, el codigo o el UID/PUK pertenecen a equipos distintos; revise los datos",
					Map.of("equipmentIds", holders.stream().map(Equipment::getId).toList()));
		}
		Equipment holder = holders.getFirst();
		if (holder.isDeleted()) {
			throw new ConflictException("EQUIPMENT_DELETED_EXISTS",
					"Ya existe un equipo eliminado con esos identificadores; recuperelo en lugar de crear otro",
					Map.of("equipmentId", holder.getId()));
		}
		throw new ConflictException("EQUIPMENT_IDENTIFIER_IN_USE",
				"La serie, el codigo o el UID/PUK ya pertenecen a otro equipo", Map.of("equipmentId", holder.getId()));
	}

	/** Tipos de conector en el mismo orden de las posiciones solicitadas. */
	private List<ConnectorType> resolveConnectorTypes(List<UUID> ids) {
		List<UUID> requested = ids == null ? List.of() : ids;
		Set<UUID> distinct = Set.copyOf(requested.stream().filter(Objects::nonNull).toList());
		Map<UUID, ConnectorType> found = connectorTypes.findAllById(distinct).stream()
				.collect(Collectors.toMap(ConnectorType::getId, Function.identity()));
		return requested.stream().map(id -> {
			ConnectorType type = id == null ? null : found.get(id);
			if (type == null) {
				throw new NotFoundException("CONNECTOR_TYPE_NOT_FOUND", "El tipo de conector no existe");
			}
			return type;
		}).toList();
	}

	private void discardAfterCommit(String reference) {
		AfterCommit.run(() -> {
			try {
				fileStorage.delete(reference);
			}
			catch (RuntimeException ex) {
				log.warn("No se pudo borrar la imagen anterior {}; queda huerfana en el almacenamiento", reference, ex);
			}
		});
	}

	private Equipment loadForChange(UUID id, long version) {
		Equipment target = load(id);
		target.requireVersion(version);
		return target;
	}

	private Equipment load(UUID id) {
		return equipment.findById(id)
				.orElseThrow(() -> new NotFoundException("EQUIPMENT_NOT_FOUND", "El equipo no existe"));
	}
}

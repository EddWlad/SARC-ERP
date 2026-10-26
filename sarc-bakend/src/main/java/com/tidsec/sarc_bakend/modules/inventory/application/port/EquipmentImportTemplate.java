package com.tidsec.sarc_bakend.modules.inventory.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.shared.report.ReportFile;

/** Puerto: genera la plantilla XLSX de carga masiva con las listas de valores validos. */
public interface EquipmentImportTemplate {

	/** {@code connectorTypes}: tipos activos del catalogo, ofrecidos en las columnas de conectores. */
	ReportFile render(List<ConnectorType> connectorTypes);
}

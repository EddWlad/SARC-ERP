package com.tidsec.sarc_bakend.modules.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

class IncidentLabelsTest {

	@Test
	void everyIncidentTypeHasADistinctSpanishLabel() {
		assertThat(java.util.Arrays.stream(IncidentType.values()).map(IncidentLabels::type)).doesNotContainNull()
				.doesNotHaveDuplicates().allSatisfy(label -> assertThat(label).isNotBlank());
		assertThat(IncidentLabels.type(IncidentType.UNCATALOGED)).isEqualTo("Incidencia no catalogada");
		assertThat(IncidentLabels.type(IncidentType.WAIT_FOR_CONNECTION)).isEqualTo("Espera de conexión");
	}

	@Test
	void everyOtherEnumHasALabel() {
		for (ElementClass value : ElementClass.values()) {
			assertThat(IncidentLabels.elementClass(value)).isNotBlank();
		}
		for (SiteElementKind value : SiteElementKind.values()) {
			assertThat(IncidentLabels.siteElementKind(value)).isNotBlank();
		}
		for (Priority value : Priority.values()) {
			assertThat(IncidentLabels.priority(value)).isNotBlank();
		}
		for (ReportOrigin value : ReportOrigin.values()) {
			assertThat(IncidentLabels.origin(value)).isNotBlank();
		}
		assertThat(IncidentLabels.state(TicketState.OPEN)).isEqualTo("Abierto");
		assertThat(IncidentLabels.state(TicketState.CLOSED)).isEqualTo("Cerrado");
	}

	@Test
	void milestoneMarksFollowTheSpecification() {
		assertThat(IncidentLabels.milestoneMark(MilestoneStatus.RECORDED, false)).isEqualTo("R");
		assertThat(IncidentLabels.milestoneMark(MilestoneStatus.RECORDED, true)).isEqualTo("R*");
		assertThat(IncidentLabels.milestoneMark(MilestoneStatus.NOT_APPLICABLE, false)).isEqualTo("N/A");
		assertThat(IncidentLabels.milestoneMark(MilestoneStatus.NO_DATA, false)).isEqualTo("S/D");
		assertThat(IncidentLabels.milestoneMark(MilestoneStatus.PENDING, false)).isEqualTo("—");
	}

	@Test
	void ticketsWithoutANetworkAreGroupedAsSinRed() {
		assertThat(IncidentLabels.network(null)).isEqualTo("Sin red");
		assertThat(IncidentLabels.network("KIA")).isEqualTo("KIA");
		assertThat(IncidentLabels.CHATBOT_SERVICE).isEqualTo("Servicio de chatbot");
	}
}

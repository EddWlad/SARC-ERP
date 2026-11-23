package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Canal por el que el analista publico el aviso a mano. {@code OTHER} exige indicar cual (data-model.md). */
public enum CommunicationChannel {

	REDAE_INPSERCOM,
	KIA_ELECTROLINERAS,
	CARGADORES_ELECTRICOS,
	AUTOEASTERN_ENERLINK,
	DIRECTIVOS,
	EMAIL,
	OTHER
}

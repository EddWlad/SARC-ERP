package com.tidsec.sarc_bakend.modules.intermittency.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDayEvent;

/** Puerto de persistencia de los dias revisados y su historial. */
public interface ReviewedDayRepository {

	Optional<ReviewedDay> find(LocalDate day);

	/** Escribe de inmediato: la version optimista debe fallar aqui y no al final de la transaccion. */
	ReviewedDay save(ReviewedDay day);

	ReviewedDayEvent saveEvent(ReviewedDayEvent event);

	/** Los dias con algun registro en {@code [from, to)}, marcados o con la marca retirada, en orden. */
	List<ReviewedDay> findInRange(LocalDate from, LocalDate to);

	/** Los dias con marca vigente en {@code [from, to)}. */
	List<LocalDate> findActiveDays(LocalDate from, LocalDate to);

	/**
	 * Toma el bloqueo transaccional de un dia: serializa dos marcas o retiros simultaneos del mismo dia hasta el
	 * final de la transaccion. Debe llamarse dentro de una transaccion.
	 */
	void lockDay(LocalDate day);
}

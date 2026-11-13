package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.WithdrawReviewedDayRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.ReviewedDayMarkResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.ReviewedDayResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.ReviewedDayService;

import jakarta.validation.Valid;

/** Dias revisados de la red KIA: la marca que acredita la cobertura de la disponibilidad estimada (FR-044). */
@RestController
@RequestMapping("/api/v1/intermittency/reviewed-days")
class ReviewedDayController {

	private final ReviewedDayService days;

	ReviewedDayController(ReviewedDayService days) {
		this.days = days;
	}

	/** Un registro por cada dia de {@code [from, to)}, con o sin marca. Sin rango, los ultimos 31 dias, hoy incluido. */
	@GetMapping
	List<ReviewedDayResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return days.list(from, to).stream().map(ReviewedDayResponse::from).toList();
	}

	/** Marca el dia como revisado; un dia que no termino se rechaza. Avisa de los lotes sin confirmar que lo tocan. */
	@PutMapping("/{day}")
	ReviewedDayMarkResponse mark(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
		return ReviewedDayMarkResponse.from(days.mark(day));
	}

	/** Retira la marca con una justificacion obligatoria. */
	@PostMapping("/{day}/withdraw")
	ReviewedDayResponse withdraw(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
			@Valid @RequestBody WithdrawReviewedDayRequest request) {
		return ReviewedDayResponse.from(days.withdraw(day, request.version(), request.justification()));
	}
}

package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDayEvent;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.ReviewedDayRepository;
import com.tidsec.sarc_bakend.shared.persistence.AdvisoryLocks;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
class ReviewedDayRepositoryAdapter implements ReviewedDayRepository {

	private final ReviewedDayJpaRepository days;
	private final ReviewedDayEventJpaRepository events;

	@PersistenceContext
	private EntityManager entityManager;

	ReviewedDayRepositoryAdapter(ReviewedDayJpaRepository days, ReviewedDayEventJpaRepository events) {
		this.days = days;
		this.events = events;
	}

	@Override
	public Optional<ReviewedDay> find(LocalDate day) {
		return days.findById(day);
	}

	@Override
	public ReviewedDay save(ReviewedDay day) {
		return days.saveAndFlush(day);
	}

	@Override
	public ReviewedDayEvent saveEvent(ReviewedDayEvent event) {
		return events.saveAndFlush(event);
	}

	@Override
	public List<ReviewedDay> findInRange(LocalDate from, LocalDate to) {
		return days.inRange(from, to);
	}

	@Override
	public List<LocalDate> findActiveDays(LocalDate from, LocalDate to) {
		return days.inRange(from, to).stream().filter(ReviewedDay::isActive).map(ReviewedDay::getDay).toList();
	}

	@Override
	public void lockDay(LocalDate day) {
		AdvisoryLocks.lock(entityManager, "itm-day:", day.toString());
	}
}

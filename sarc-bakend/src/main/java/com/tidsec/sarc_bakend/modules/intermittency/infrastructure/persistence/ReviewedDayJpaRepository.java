package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDay;

interface ReviewedDayJpaRepository extends JpaRepository<ReviewedDay, LocalDate> {

	@Query("SELECT d FROM ReviewedDay d WHERE d.day >= :from AND d.day < :to ORDER BY d.day")
	List<ReviewedDay> inRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}

package com.tidsec.sarc_bakend.modules.reporting.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.modules.reporting.domain.repository.IssuedReportRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class IssuedReportRepositoryAdapter implements IssuedReportRepository {

	private final IssuedReportJpaRepository jpa;

	IssuedReportRepositoryAdapter(IssuedReportJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public IssuedReport save(IssuedReport report) {
		return jpa.save(report);
	}

	@Override
	public Optional<IssuedReport> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public PageResult<IssuedReport> search(String kind, PageQuery pageQuery) {
		PageRequest request = PageRequest.of(pageQuery.page(), pageQuery.size(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
		Page<IssuedReport> page = kind == null ? jpa.findAll(request) : jpa.findByKind(kind, request);
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}
}

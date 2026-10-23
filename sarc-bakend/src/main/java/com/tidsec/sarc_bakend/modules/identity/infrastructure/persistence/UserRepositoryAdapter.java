package com.tidsec.sarc_bakend.modules.identity.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserSearchCriteria;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class UserRepositoryAdapter implements UserRepository {

	private static final char LIKE_ESCAPE = '\\';

	private final UserJpaRepository jpa;

	UserRepositoryAdapter(UserJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public UserAccount save(UserAccount user) {
		return jpa.save(user);
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public Optional<UserAccount> findByEmail(String normalizedEmail) {
		return jpa.findByEmail(normalizedEmail);
	}

	@Override
	public boolean existsByEmail(String normalizedEmail) {
		return jpa.existsByEmail(normalizedEmail);
	}

	@Override
	public boolean existsAny() {
		return jpa.count() > 0;
	}

	@Override
	public PageResult<UserAccount> search(UserSearchCriteria criteria, PageQuery pageQuery) {
		PageRequest request = PageRequest.of(pageQuery.page(), pageQuery.size(),
				Sort.by("fullName").ascending().and(Sort.by("id")));
		Page<UserAccount> page = jpa.findAll(matching(criteria), request);
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	private static Specification<UserAccount> matching(UserSearchCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.text() != null && !criteria.text().isBlank()) {
				String pattern = "%" + escapeLike(criteria.text().strip().toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
						cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE)));
			}
			if (criteria.role() != null) {
				predicates.add(cb.isMember(criteria.role(), root.<Set<Role>>get("roles")));
			}
			if (criteria.status() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.status()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}

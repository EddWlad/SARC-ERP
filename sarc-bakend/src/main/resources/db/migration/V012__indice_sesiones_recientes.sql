-- Feature 004: pagina el listado de sesiones en su orden sin ordenar todo el periodo.
CREATE INDEX ix_energy_session_started ON energy_session (started_at DESC, id);

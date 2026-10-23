package com.tidsec.sarc_bakend.shared.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.tidsec.sarc_bakend.shared.web.RequestTraceFilter;

/** Unico punto de traduccion de errores a application/problem+json (RFC 9457). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(SarcException.class)
	ResponseEntity<Object> handleSarc(SarcException ex, WebRequest request) {
		HttpStatus status = switch (ex) {
			case NotFoundException _ -> HttpStatus.NOT_FOUND;
			case ConflictException _ -> HttpStatus.CONFLICT;
			case BusinessRuleException _ -> HttpStatus.UNPROCESSABLE_CONTENT;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
		return problem(status, ex.code(), ex.getMessage(), request);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
		if (ex instanceof InsufficientAuthenticationException) {
			return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Se requiere autenticacion", request);
		}
		return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales invalidas", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
		return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tiene permisos para esta operacion", request);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, WebRequest request) {
		return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
				"El recurso fue modificado por otro usuario; recargue e intente de nuevo", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
		log.warn("Violacion de integridad de datos", ex);
		return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
				"La operacion viola una restriccion de integridad de datos", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Error no controlado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
				.toList();
		return validationProblem(errors, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(e -> new FieldError(result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
				.toList();
		return validationProblem(errors, request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
			WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			problem.setProperty("traceId", MDC.get(RequestTraceFilter.TRACE_ID_KEY));
			if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
				problem.setProperty("code", "HTTP_" + statusCode.value());
			}
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private ResponseEntity<Object> validationProblem(List<FieldError> errors, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("fieldErrors", errors);
		return createResponseEntity(problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
	}

	private ResponseEntity<Object> problem(HttpStatus status, String code, String detail, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		return createResponseEntity(problem, new HttpHeaders(), status, request);
	}

	public record FieldError(String field, String message) {
	}
}

package com.gesfut.exceptions;

import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Criterio de estados:
 * - 404: el recurso no existe.
 * - 400: datos de entrada inválidos o regla de negocio incumplida (mensaje para el usuario).
 * - 409: el recurso ya existe.
 * - 403: sin permiso / credenciales inválidas.
 * - 500: error inesperado, se registra como ERROR y no se exponen detalles internos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? "" : message));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class, IllegalStateException.class, TeamDisableException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        // Auditoria 22/10: un codigo con formato invalido es un recurso inexistente (404), sin mensaje interno en ingles.
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null && ex.getMessage().startsWith("Invalid UUID string")) {
            return body(HttpStatus.NOT_FOUND, "No encontramos un torneo con ese código.");
        }
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Auditoria 22/10: login con un email inexistente daba 500. */
    @ExceptionHandler(org.springframework.security.authentication.InternalAuthenticationServiceException.class)
    public ResponseEntity<Map<String, String>> handleInternalAuth(Exception ex) {
        if (ex.getCause() instanceof ResourceNotFoundException) return body(HttpStatus.NOT_FOUND, "El email ingresado no existe.");
        return handleUnexpected(ex);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(BadCredentialsException ex) {
        return body(HttpStatus.FORBIDDEN, "Contraseña inválida.");
    }

    /** Auditoria 22/10: endpoint protegido llamado sin token: 401 en vez de 500. */
    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoCredentials(Exception ex) {
        return body(HttpStatus.UNAUTHORIZED, "Tenés que iniciar sesión.");
    }

    /** Auditoria 22/10: si el SMTP falla, avisar con 503 y mensaje claro en vez de 500. */
    /** Auditoria 22/10 (#16): devolver todos los errores de validacion juntos, en orden de campo. */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(java.util.Comparator.comparing(org.springframework.validation.FieldError::getField))
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        String message = String.join(" ", fields.values());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message, "errors", fields));
    }

    @ExceptionHandler(org.springframework.mail.MailException.class)
    public ResponseEntity<Map<String, String>> handleMail(Exception ex) {
        log.warn("Fallo el envio de mail: {}", ex.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos enviar el correo. Probá de nuevo en unos minutos.");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "No tenés permiso para esta operación.");
    }

    /** Los servicios usan RuntimeException "pelada" para reglas de negocio con mensaje al usuario: 400. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        if (ex.getClass() == RuntimeException.class) return body(HttpStatus.BAD_REQUEST, ex.getMessage());
        return handleUnexpected(ex);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        // Errores propios de Spring MVC (405, ruta inexistente, 415, parámetro faltante...): respetan su status.
        if (ex instanceof org.springframework.web.ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            String detail = er.getBody().getDetail();
            return body(status != null ? status : HttpStatus.BAD_REQUEST, detail != null ? detail : "Solicitud inválida.");
        }
        log.error("Error inesperado procesando la solicitud", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intentá nuevamente.");
    }
}

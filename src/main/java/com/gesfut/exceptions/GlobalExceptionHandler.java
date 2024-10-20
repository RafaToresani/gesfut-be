package com.gesfut.exceptions;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex){
        return ex.getMessage();
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequestException(BadRequestException ex){
        return ex.getMessage();
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleBadCredentialsException(BadCredentialsException ex){
        return "Contraseña inválida.";
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleRuntimeException(RuntimeException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(TeamDisableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleTeamDisableException(TeamDisableException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalArgumentException(IllegalArgumentException ex){
        return ex.getMessage();
    }
}

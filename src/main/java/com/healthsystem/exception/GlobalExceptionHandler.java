package com.healthsystem.exception;
import com.healthsystem.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException ex) { return error(ex.status(),ex.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class,ConstraintViolationException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception ex) { return error(400,"Invalid request parameters"); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> missing(Exception ex) { return error(404,"Resource not found"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> integrity(Exception ex) { return error(409,"Conflicting data or references"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        // No request bodies, SQL, clinical values or credentials in this message.
        LOG.error("Unhandled request error type={}",ex.getClass().getName());
        return error(500,"Internal server error");
    }
    private ResponseEntity<ApiResponse<Void>> error(int status,String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status,message,null));
    }
}

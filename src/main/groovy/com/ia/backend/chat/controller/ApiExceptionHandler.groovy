package com.ia.backend.chat.controller

import com.ia.backend.chat.client.CompletionsClientException
import com.ia.backend.chat.service.ConversationAccessDeniedException
import com.ia.backend.chat.service.ConversationNotFoundException
import com.ia.backend.chat.service.DuplicateUserException
import com.ia.backend.chat.service.MessageNotFoundException
import com.ia.backend.chat.service.MessageValidationException
import com.ia.backend.chat.service.UserNotFoundException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Slf4j
@CompileStatic
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		def problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, 'Los datos enviados no son válidos')
		problem.title = 'Solicitud inválida'
		def fieldErrors = ex.bindingResult.fieldErrors.collect { error ->
			[field: error.field, message: error.defaultMessage]
		}
		problem.setProperty('errors', fieldErrors)
		problem
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException)
	ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"El valor '${ex.value}' no es válido para el parámetro '${ex.name}'")
	}

	@ExceptionHandler(HttpMessageNotReadableException)
	ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, 'El cuerpo de la petición está mal formado o no es JSON válido')
	}

	@ExceptionHandler(UserNotFoundException)
	ProblemDetail handleUserNotFound(UserNotFoundException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message)
	}

	@ExceptionHandler(ConversationNotFoundException)
	ProblemDetail handleConversationNotFound(ConversationNotFoundException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message)
	}

	@ExceptionHandler(MessageNotFoundException)
	ProblemDetail handleMessageNotFound(MessageNotFoundException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message)
	}

	@ExceptionHandler(ConversationAccessDeniedException)
	ProblemDetail handleConversationAccessDenied(ConversationAccessDeniedException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message)
	}

	@ExceptionHandler(MessageValidationException)
	ProblemDetail handleMessageValidation(MessageValidationException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message)
	}

	@ExceptionHandler(DuplicateUserException)
	ProblemDetail handleDuplicate(DuplicateUserException ex) {
		ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message)
	}

	@ExceptionHandler(DataIntegrityViolationException)
	ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn('Violación de integridad de datos', ex)
		ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'La operación viola una restricción de integridad de la base de datos')
	}

	@ExceptionHandler(CompletionsClientException)
	ProblemDetail handleCompletionsClient(CompletionsClientException ex) {
		// La API externa falló o respondió algo inesperado: es un fallo del "gateway"
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message)
	}

	@ExceptionHandler(Exception)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error('Error no controlado', ex)
		ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, 'Error interno del servidor')
	}
}

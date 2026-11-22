package pt.ua.kinetiq.fleet.lifecycle.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pt.ua.kinetiq.fleet.lifecycle.domain.InvalidTransitionException;
import pt.ua.kinetiq.fleet.lifecycle.domain.TransitionNotPermittedException;
import pt.ua.kinetiq.fleet.lifecycle.service.BikeNotFoundException;

/**
 * Converte as exceções em respostas JSON (RFC 9457 "problem details"):
 * 404 bicicleta não existe · 409 transição inválida ou conflito · 403 papel sem permissão.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BikeNotFoundException.class)
    public ProblemDetail notFound(BikeNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Bike not found");
        return problem;
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ProblemDetail invalidTransition(InvalidTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Invalid state transition");
        problem.setProperty("currentState", e.getCurrentState());
        problem.setProperty("event", e.getEvent());
        problem.setProperty("allowedEvents", e.getAllowedEvents());
        return problem;
    }

    @ExceptionHandler(TransitionNotPermittedException.class)
    public ProblemDetail notPermitted(TransitionNotPermittedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Transition not permitted for this role");
        problem.setProperty("currentState", e.getCurrentState());
        problem.setProperty("event", e.getEvent());
        problem.setProperty("role", e.getRole());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrentChange(OptimisticLockingFailureException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The bike changed state while this request was being processed. Reload its state and try again.");
        problem.setTitle("Concurrent state change");
        return problem;
    }
}

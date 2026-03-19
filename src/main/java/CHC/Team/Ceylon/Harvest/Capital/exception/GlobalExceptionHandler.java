package CHC.Team.Ceylon.Harvest.Capital.exception;

import CHC.Team.Ceylon.Harvest.Capital.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FarmerDashboardException.class)
    public ResponseEntity<ApiErrorResponse> handleFarmerDashboardException(FarmerDashboardException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(ex.getMessage(), LocalDateTime.now()));
    }
}

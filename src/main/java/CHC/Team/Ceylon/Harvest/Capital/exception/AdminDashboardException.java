package CHC.Team.Ceylon.Harvest.Capital.exception;

/**
 * Thrown when the AdminDashboardService encounters an unrecoverable error
 * while aggregating platform data. Mapped to HTTP 500 in GlobalExceptionHandler.
 */
public class AdminDashboardException extends RuntimeException {

    public AdminDashboardException(String message) {
        super(message);
    }

    public AdminDashboardException(String message, Throwable cause) {
        super(message, cause);
    }
}

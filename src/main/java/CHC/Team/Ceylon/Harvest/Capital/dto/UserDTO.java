package CHC.Team.Ceylon.Harvest.Capital.dto;

/**
 * Lightweight user representation used inside AdminDashboardResponseDTO.
 * Exposes only the fields the dashboard front-end requires.
 */
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String role;

    /**
     * Reflects the user's verification_status (PENDING / VERIFIED / REJECTED /
     * NOT_SUBMITTED). The front-end treats this as the "Account Status" column.
     */
    private String status;

    // ── Constructors ──────────────────────────────────────────────────────────

    public UserDTO() {
    }

    public UserDTO(Long id, String name, String email, String role, String status) {
        this.id     = id;
        this.name   = name;
        this.email  = email;
        this.role   = role;
        this.status = status;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

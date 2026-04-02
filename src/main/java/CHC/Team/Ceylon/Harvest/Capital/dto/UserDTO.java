package CHC.Team.Ceylon.Harvest.Capital.dto;

/**
 * Lightweight user representation used inside AdminDashboardResponseDTO.
 * Exposes only the fields the dashboard front-end requires.
 */
public class UserDTO {

    private Long   id;
    private String name;
    private String email;
    private String role;

    /** verification_status: PENDING / VERIFIED / REJECTED / NOT_SUBMITTED */
    private String status;

    /** AC-1: account_status: ACTIVE / SUSPENDED */
    private String accountStatus;

    public UserDTO() {}

    /**
     * Backwards-compatible 5-arg constructor — existing tests use this signature.
     * accountStatus defaults to "ACTIVE".
     */
    public UserDTO(Long id, String name, String email, String role, String status) {
        this(id, name, email, role, status, "ACTIVE");
    }

    public UserDTO(Long id, String name, String email, String role,
                   String status, String accountStatus) {
        this.id            = id;
        this.name          = name;
        this.email         = email;
        this.role          = role;
        this.status        = status;
        this.accountStatus = accountStatus;
    }

    public Long   getId()            { return id; }
    public void   setId(Long id)     { this.id = id; }

    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }

    public String getEmail()         { return email; }
    public void   setEmail(String v) { this.email = v; }

    public String getRole()          { return role; }
    public void   setRole(String v)  { this.role = v; }

    public String getStatus()          { return status; }
    public void   setStatus(String v)  { this.status = v; }

    public String getAccountStatus()         { return accountStatus; }
    public void   setAccountStatus(String v) { this.accountStatus = v; }
}

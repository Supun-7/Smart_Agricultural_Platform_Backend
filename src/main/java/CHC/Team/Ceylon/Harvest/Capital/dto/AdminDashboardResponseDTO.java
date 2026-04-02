package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;
import java.util.List;

public class AdminDashboardResponseDTO {

    private int totalFarmers;
    private int totalInvestors;
    private BigDecimal totalInvestment;
    private List<UserDTO> farmers;
    private List<UserDTO> investors;
    private List<UserDTO> auditors;
    private List<UserDTO> admins;
    private List<UserDTO> systemAdmins;
    private List<AdminAuditLogDTO> auditLogs;

    public AdminDashboardResponseDTO() {}

    public AdminDashboardResponseDTO(
            int totalFarmers,
            int totalInvestors,
            BigDecimal totalInvestment,
            List<UserDTO> farmers,
            List<UserDTO> investors,
            List<UserDTO> auditors,
            List<UserDTO> admins,
            List<UserDTO> systemAdmins,
            List<AdminAuditLogDTO> auditLogs) {
        this.totalFarmers = totalFarmers;
        this.totalInvestors = totalInvestors;
        this.totalInvestment = totalInvestment;
        this.farmers = farmers;
        this.investors = investors;
        this.auditors = auditors;
        this.admins = admins;
        this.systemAdmins = systemAdmins;
        this.auditLogs = auditLogs;
    }

    public int getTotalFarmers() { return totalFarmers; }
    public void setTotalFarmers(int totalFarmers) { this.totalFarmers = totalFarmers; }

    public int getTotalInvestors() { return totalInvestors; }
    public void setTotalInvestors(int totalInvestors) { this.totalInvestors = totalInvestors; }

    public BigDecimal getTotalInvestment() { return totalInvestment; }
    public void setTotalInvestment(BigDecimal totalInvestment) { this.totalInvestment = totalInvestment; }

    public List<UserDTO> getFarmers() { return farmers; }
    public void setFarmers(List<UserDTO> farmers) { this.farmers = farmers; }

    public List<UserDTO> getInvestors() { return investors; }
    public void setInvestors(List<UserDTO> investors) { this.investors = investors; }

    public List<UserDTO> getAuditors() { return auditors; }
    public void setAuditors(List<UserDTO> auditors) { this.auditors = auditors; }

    public List<UserDTO> getAdmins() { return admins; }
    public void setAdmins(List<UserDTO> admins) { this.admins = admins; }

    public List<UserDTO> getSystemAdmins() { return systemAdmins; }
    public void setSystemAdmins(List<UserDTO> systemAdmins) { this.systemAdmins = systemAdmins; }

    public List<AdminAuditLogDTO> getAuditLogs() { return auditLogs; }
    public void setAuditLogs(List<AdminAuditLogDTO> auditLogs) { this.auditLogs = auditLogs; }
}

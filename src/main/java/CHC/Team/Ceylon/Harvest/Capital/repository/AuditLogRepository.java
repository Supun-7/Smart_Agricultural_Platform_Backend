package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read / write access to the audit_log table.
 * No delete or update methods are exposed (CHC-207, AC-5).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Returns all log entries for a given auditor, most-recent first (AC-4).
     * The auditor relationship is eagerly joined to avoid N+1 queries.
     */
    @Query("""
            select a
            from AuditLog a
            join fetch a.auditor u
            where u.userId = :auditorId
            order by a.actionedAt desc
            """)
    List<AuditLog> findAllByAuditorIdOrderByActionedAtDesc(@Param("auditorId") Long auditorId);
}

package dev.kuratoon.ingestion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    /** Source folders owned by a job that is not finished. */
    @Query("select j.sourceFolder from ImportJob j where j.finishedAt is null")
    List<String> findActiveSourceFolders();

    /** The oldest QUEUED job id, skipping rows another worker holds. */
    @Query(value = """
            select id from import_job where status = 'QUEUED'
            order by created_at, id
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<UUID> lockNextQueued();

    List<ImportJob> findByStatusIn(Collection<ImportStatus> statuses);

    /** @param statuses null for all */
    @Query("""
            select j from ImportJob j
            where (:statuses is null or j.status in :statuses)
            """)
    Page<ImportJob> search(@Param("statuses") Collection<ImportStatus> statuses, Pageable pageable);
}

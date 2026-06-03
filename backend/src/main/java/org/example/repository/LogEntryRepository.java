package org.example.repository;

import org.example.entity.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByIssueIdOrderByTimestampAsc(Long issueId);

    Optional<LogEntry> findByFingerprint(String fingerprint);
}

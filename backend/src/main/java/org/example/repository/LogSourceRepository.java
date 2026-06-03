package org.example.repository;

import org.example.entity.LogSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogSourceRepository extends JpaRepository<LogSource, Long> {
    List<LogSource> findByEnabledTrue();
}

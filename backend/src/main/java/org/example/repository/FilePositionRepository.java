package org.example.repository;

import org.example.entity.FilePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FilePositionRepository extends JpaRepository<FilePosition, Long> {
    Optional<FilePosition> findBySourceIdAndFilePath(Long sourceId, String filePath);
}

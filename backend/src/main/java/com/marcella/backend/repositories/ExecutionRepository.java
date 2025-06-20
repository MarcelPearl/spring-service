package com.marcella.backend.repositories;

import com.marcella.backend.entities.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    List<Execution> findByWorkflowId(UUID workflowId);
    List<Execution> findByOwnerId(UUID ownerId);
    List<Execution> findTop5ByOwnerIdOrderByStartedAtDesc(UUID ownerId);
    List<Execution> findByOwnerIdAndStatusIgnoreCase(UUID ownerId, String status);
    long countByOwnerIdAndStatusIgnoreCase(UUID ownerId, String status);

}

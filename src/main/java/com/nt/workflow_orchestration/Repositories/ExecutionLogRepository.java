package com.nt.workflow_orchestration.Repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nt.workflow_orchestration.Entities.ExecutionLog;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    List<ExecutionLog> findByWorkflowIdOrderByTimestampDesc(Long workflowId);

    List<ExecutionLog> findByTaskIdOrderByTimestampDesc(Long taskId);
}

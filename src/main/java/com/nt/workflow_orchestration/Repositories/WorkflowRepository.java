package com.nt.workflow_orchestration.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nt.workflow_orchestration.Entities.Workflow;

public interface WorkflowRepository extends JpaRepository<Workflow, Long>{
    
}

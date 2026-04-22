package com.nt.workflow_orchestration.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nt.workflow_orchestration.Entities.Task;

import java.util.List;

public interface TaskRepository

extends JpaRepository<Task, Long> {

List<Task> findByWorkflowId(Long workflowId);

List<Task> findByWorkflowIdOrderByTaskOrder(Long workflowId);

}
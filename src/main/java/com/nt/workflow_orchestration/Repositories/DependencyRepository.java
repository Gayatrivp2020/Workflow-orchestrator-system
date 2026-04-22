package com.nt.workflow_orchestration.Repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nt.workflow_orchestration.Entities.Dependency;

public interface DependencyRepository extends JpaRepository<Dependency, Long>{

List<Dependency> findByTaskId(Long taskId);    
}

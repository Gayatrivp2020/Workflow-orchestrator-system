package com.nt.workflow_orchestration.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tasks")

@Data
@NoArgsConstructor
@AllArgsConstructor

public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long taskId;

    private String taskName;

    private String taskType;

    private String status;

    private Integer taskOrder;

    private Integer retryCount;

    private Integer maxRetries;

    private Long workflowId;

}
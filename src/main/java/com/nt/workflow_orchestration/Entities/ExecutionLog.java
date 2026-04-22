package com.nt.workflow_orchestration.Entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "execution_logs")

@Data
@NoArgsConstructor
@AllArgsConstructor

public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private Long workflowId;

    private Long taskId;

    private String taskName;

    private String action;

    @Column(length = 1000)
    private String message;

    private LocalDateTime timestamp;
}

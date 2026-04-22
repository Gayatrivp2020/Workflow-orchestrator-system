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
@Table(name="workflows")

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long workflowId;

    private String workflowName;

    private String status;

    private Boolean paused;

    private Boolean isTerminated;

}

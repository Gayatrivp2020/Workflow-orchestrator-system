package com.nt.workflow_orchestration.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="dependencies")

@Data
@NoArgsConstructor
@AllArgsConstructor

public class Dependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long dependencyId;

    private Long taskId;

    private Long dependsOnTaskId;

}


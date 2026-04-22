package com.nt.workflow_orchestration.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nt.workflow_orchestration.Entities.Dependency;
import com.nt.workflow_orchestration.Entities.ExecutionLog;
import com.nt.workflow_orchestration.Entities.Task;
import com.nt.workflow_orchestration.Entities.Workflow;
import com.nt.workflow_orchestration.Service.WorkflowService;

@RestController
@RequestMapping("/api/workflow")
@CrossOrigin

public class WorkflowController {

    private final WorkflowService workflowService;


    public WorkflowController(
            WorkflowService workflowService) {

        this.workflowService = workflowService;
    }



    // ========== GET ALL WORKFLOWS ==========

    @GetMapping("/all")
    public List<Workflow> getAllWorkflows() {
        return workflowService.getAllWorkflows();
    }



    // ========== START (CREATE) WORKFLOW ==========

    @PostMapping("/start")
    public Workflow startWorkflow() throws Exception {
        return workflowService.createWorkflow();
    }



    // ========== EXECUTE WORKFLOW ==========

    @PostMapping("/execute/{workflowId}")
    public List<Task> executeWorkflow(
            @PathVariable Long workflowId) {

        return workflowService
                .executeWorkflow(workflowId);
    }



    // ========== PAUSE WORKFLOW ==========

    @PostMapping("/pause/{workflowId}")
    public String pauseWorkflow(
            @PathVariable Long workflowId) {

        workflowService.pauseWorkflow(workflowId);
        return "Workflow Paused";
    }



    // ========== RESUME WORKFLOW ==========

    @PostMapping("/resume/{workflowId}")
    public String resumeWorkflow(
            @PathVariable Long workflowId) {

        workflowService.resumeWorkflow(workflowId);
        return "Workflow Resumed";
    }



    // ========== TERMINATE WORKFLOW ==========

    @PostMapping("/terminate/{workflowId}")
    public String terminateWorkflow(
            @PathVariable Long workflowId) {

        workflowService.terminateWorkflow(workflowId);
        return "Workflow Terminated";
    }



    // ========== GET TASK STATUS ==========

    @GetMapping("/status/{workflowId}")
    public List<Task> getStatus(
            @PathVariable Long workflowId) {

        return workflowService
                .getTasks(workflowId);
    }



    // ========== APPROVE HUMAN TASK ==========

    @PostMapping("/approve/{taskId}")
    public String approveTask(
            @PathVariable Long taskId) {

        workflowService.approveTask(taskId);
        return "Task Approved";
    }



    // ========== RETRY FAILED TASK ==========

    @PostMapping("/retry/{taskId}")
    public String retryTask(
            @PathVariable Long taskId) {

        workflowService.retryTask(taskId);
        return "Task queued for retry";
    }



    // ========== GET DEPENDENCY GRAPH ==========

    @GetMapping("/{workflowId}/dependencies")
    public List<Dependency> getDependencies(
            @PathVariable Long workflowId) {

        return workflowService
                .getDependencies(workflowId);
    }



    // ========== GET EXECUTION LOGS ==========

    @GetMapping("/{workflowId}/logs")
    public List<ExecutionLog> getLogs(
            @PathVariable Long workflowId) {

        return workflowService
                .getLogs(workflowId);
    }

}
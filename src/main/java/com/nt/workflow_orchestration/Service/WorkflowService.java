package com.nt.workflow_orchestration.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nt.workflow_orchestration.Entities.*;
import com.nt.workflow_orchestration.Repositories.*;
import com.nt.workflow_orchestration.model.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepo;
    private final TaskRepository taskRepo;
    private final DependencyRepository dependencyRepo;
    private final ExecutionLogRepository logRepo;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService;

    // Maps task names to their worker service URLs
    private static final Map<String, String> SERVICE_URL_MAP = new HashMap<>();

    static {
        SERVICE_URL_MAP.put("Payment", "http://localhost:8080/payment-service");
        SERVICE_URL_MAP.put("Inventory", "http://localhost:8080/inventory-service");
        SERVICE_URL_MAP.put("Notification", "http://localhost:8080/notification-service");
        SERVICE_URL_MAP.put("Shipping", "http://localhost:8080/shipping-service");
    }


    public WorkflowService(
            WorkflowRepository workflowRepo,
            TaskRepository taskRepo,
            DependencyRepository dependencyRepo,
            ExecutionLogRepository logRepo,
            RestTemplate restTemplate) {

        this.workflowRepo = workflowRepo;
        this.taskRepo = taskRepo;
        this.dependencyRepo = dependencyRepo;
        this.logRepo = logRepo;
        this.restTemplate = restTemplate;
        this.executorService = Executors.newFixedThreadPool(5);
    }



    // ==================== CREATE WORKFLOW ====================

    public Workflow createWorkflow() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("workflow.json");

        if (input == null) {
            throw new IllegalStateException("workflow.json not found in classpath");
        }

        WorkflowDefinition def = mapper.readValue(input, WorkflowDefinition.class);

        Workflow wf = workflowRepo.save(
                new Workflow(null, def.getWorkflowName(), "CREATED", false, false)
        );

        log.info("Created workflow: {} (ID: {})", wf.getWorkflowName(), wf.getWorkflowId());
        saveLog(wf.getWorkflowId(), null, null, "WORKFLOW_CREATED",
                "Workflow '" + wf.getWorkflowName() + "' created");

        Map<String, Task> taskMap = new HashMap<>();
        int order = 1;

        // First pass: create all tasks with PENDING status
        for (TaskDefinition td : def.getTasks()) {

            String taskType = (td.getType() != null) ? td.getType() : "HTTP";

            Task task = taskRepo.save(
                    new Task(
                            null,
                            td.getName(),
                            taskType,
                            "PENDING",
                            order++,
                            0,
                            3,
                            wf.getWorkflowId()
                    )
            );

            taskMap.put(td.getName(), task);

            log.info("Created task: {} (ID: {}, Type: {})",
                    task.getTaskName(), task.getTaskId(), taskType);

            saveLog(wf.getWorkflowId(), task.getTaskId(), task.getTaskName(),
                    "TASK_CREATED", "Task '" + task.getTaskName() + "' created [type=" + taskType + "]");
        }

        // Second pass: create dependency edges
        for (TaskDefinition td : def.getTasks()) {

            if (td.getDependsOn() != null) {

                Task child = taskMap.get(td.getName());
                Task parent = taskMap.get(td.getDependsOn());

                if (child == null || parent == null) {
                    log.warn("Invalid dependency: {} -> {}", td.getName(), td.getDependsOn());
                    continue;
                }

                dependencyRepo.save(
                        new Dependency(null, child.getTaskId(), parent.getTaskId())
                );

                log.info("Dependency: {} (ID:{}) depends on {} (ID:{})",
                        td.getName(), child.getTaskId(),
                        td.getDependsOn(), parent.getTaskId());

                saveLog(wf.getWorkflowId(), child.getTaskId(), child.getTaskName(),
                        "DEPENDENCY_SET", "Depends on '" + td.getDependsOn() + "'");
            }
        }

        return wf;
    }



    // ==================== EXECUTE WORKFLOW (DAG-based, parallel) ====================

    public List<Task> executeWorkflow(Long workflowId) {

        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Workflow not found with ID: " + workflowId));

        if (wf.getPaused()) {
            log.warn("Workflow {} is PAUSED — skipping execution", workflowId);
            return taskRepo.findByWorkflowIdOrderByTaskOrder(workflowId);
        }

        if (wf.getIsTerminated()) {
            log.warn("Workflow {} is TERMINATED — skipping execution", workflowId);
            return taskRepo.findByWorkflowIdOrderByTaskOrder(workflowId);
        }

        // Move from CREATED to RUNNING on first execute
        if ("CREATED".equals(wf.getStatus())) {
            wf.setStatus("RUNNING");
            workflowRepo.save(wf);
            saveLog(workflowId, null, null, "WORKFLOW_STARTED", "Workflow execution started");
        }

        List<Task> tasks = taskRepo.findByWorkflowIdOrderByTaskOrder(workflowId);

        // Find all tasks that are ready to run (all dependencies COMPLETED)
        List<Task> readyTasks = new ArrayList<>();

        for (Task task : tasks) {

            if ("PENDING".equals(task.getStatus()) || "RETRYING".equals(task.getStatus())) {

                List<Dependency> deps = dependencyRepo.findByTaskId(task.getTaskId());
                boolean canRun = true;

                for (Dependency dep : deps) {
                    Task parentTask = taskRepo.findById(dep.getDependsOnTaskId()).orElse(null);
                    if (parentTask == null || !"COMPLETED".equals(parentTask.getStatus())) {
                        canRun = false;
                        break;
                    }
                }

                if (canRun) {
                    readyTasks.add(task);
                }
            }
        }

        // Execute all ready tasks IN PARALLEL using CompletableFuture
        if (!readyTasks.isEmpty()) {

            log.info("Executing {} tasks in parallel for workflow {}",
                    readyTasks.size(), workflowId);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Task task : readyTasks) {
                futures.add(CompletableFuture.runAsync(
                        () -> executeTask(task, workflowId), executorService));
            }

            // Wait for all parallel tasks to complete
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error waiting for parallel tasks: {}", e.getMessage());
            }
        }

        // Refresh tasks and check overall workflow status
        List<Task> updatedTasks = taskRepo.findByWorkflowIdOrderByTaskOrder(workflowId);
        updateWorkflowStatus(wf, updatedTasks);

        return updatedTasks;
    }



    // ==================== EXECUTE SINGLE TASK ====================

    private void executeTask(Task task, Long workflowId) {

        log.info("Executing task: {} (ID: {}, Type: {})",
                task.getTaskName(), task.getTaskId(), task.getTaskType());

        // Mark as RUNNING
        task.setStatus("RUNNING");
        taskRepo.save(task);
        saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                "TASK_RUNNING", "Task started executing");

        // HUMAN tasks: set to WAITING_APPROVAL and stop
        if ("HUMAN".equals(task.getTaskType())) {
            task.setStatus("WAITING_APPROVAL");
            taskRepo.save(task);
            saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                    "WAITING_APPROVAL", "Waiting for human approval");
            log.info("Task {} is HUMAN type — waiting for approval", task.getTaskName());
            return;
        }

        // HTTP tasks: call the worker microservice
        try {
            String serviceUrl = SERVICE_URL_MAP.get(task.getTaskName());

            if (serviceUrl != null) {

                log.info("Calling worker service: {} for task {}",
                        serviceUrl, task.getTaskName());

                @SuppressWarnings("unchecked")
                Map<String, String> response = restTemplate.getForObject(serviceUrl, Map.class);

                if (response != null && "success".equals(response.get("status"))) {

                    task.setStatus("COMPLETED");
                    taskRepo.save(task);
                    saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                            "TASK_COMPLETED",
                            "Service responded: " + response.get("message"));

                    log.info("Task {} COMPLETED successfully", task.getTaskName());

                } else {
                    handleTaskFailure(task, workflowId,
                            "Service returned non-success: " + response);
                }

            } else {
                // No service URL mapped — auto-complete
                task.setStatus("COMPLETED");
                taskRepo.save(task);
                saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                        "TASK_COMPLETED", "No service URL mapped — auto-completed");
                log.info("Task {} auto-completed (no service URL)", task.getTaskName());
            }

        } catch (Exception e) {
            log.error("Error executing task {}: {}", task.getTaskName(), e.getMessage());
            handleTaskFailure(task, workflowId, "HTTP call failed: " + e.getMessage());
        }
    }



    // ==================== HANDLE TASK FAILURE WITH RETRY ====================

    private void handleTaskFailure(Task task, Long workflowId, String reason) {

        task.setRetryCount(task.getRetryCount() + 1);

        if (task.getRetryCount() <= task.getMaxRetries()) {
            task.setStatus("RETRYING");
            saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                    "TASK_RETRYING",
                    "Retry " + task.getRetryCount() + "/" + task.getMaxRetries()
                            + " — Reason: " + reason);
            log.warn("Task {} failed, retrying ({}/{}): {}",
                    task.getTaskName(), task.getRetryCount(), task.getMaxRetries(), reason);
        } else {
            task.setStatus("FAILED");
            saveLog(workflowId, task.getTaskId(), task.getTaskName(),
                    "TASK_FAILED",
                    "Max retries exceeded — Reason: " + reason);
            log.error("Task {} FAILED after {} retries: {}",
                    task.getTaskName(), task.getMaxRetries(), reason);
        }

        taskRepo.save(task);
    }



    // ==================== UPDATE WORKFLOW STATUS ====================

    private void updateWorkflowStatus(Workflow wf, List<Task> tasks) {

        boolean allCompleted = tasks.stream()
                .allMatch(t -> "COMPLETED".equals(t.getStatus()));

        boolean anyFailed = tasks.stream()
                .anyMatch(t -> "FAILED".equals(t.getStatus()));

        if (allCompleted) {
            wf.setStatus("COMPLETED");
            workflowRepo.save(wf);
            saveLog(wf.getWorkflowId(), null, null,
                    "WORKFLOW_COMPLETED", "All tasks completed successfully");
            log.info("Workflow {} COMPLETED", wf.getWorkflowId());

        } else if (anyFailed) {
            wf.setStatus("FAILED");
            workflowRepo.save(wf);
            saveLog(wf.getWorkflowId(), null, null,
                    "WORKFLOW_FAILED", "One or more tasks failed");
            log.warn("Workflow {} FAILED", wf.getWorkflowId());
        }
    }



    // ==================== APPROVE HUMAN TASK ====================

    public void approveTask(Long taskId) {

        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Task not found with ID: " + taskId));

        if (!"WAITING_APPROVAL".equals(task.getStatus())) {
            throw new IllegalStateException(
                    "Task '" + task.getTaskName() + "' is not waiting for approval. "
                            + "Current status: " + task.getStatus());
        }

        task.setStatus("COMPLETED");
        taskRepo.save(task);

        saveLog(task.getWorkflowId(), task.getTaskId(), task.getTaskName(),
                "TASK_APPROVED", "Human approval granted — task completed");

        log.info("Task {} (ID:{}) approved and completed", task.getTaskName(), taskId);
    }



    // ==================== RETRY FAILED TASK ====================

    public void retryTask(Long taskId) {

        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Task not found with ID: " + taskId));

        if (!"FAILED".equals(task.getStatus())) {
            throw new IllegalStateException(
                    "Task '" + task.getTaskName() + "' is not in FAILED status. "
                            + "Current status: " + task.getStatus());
        }

        task.setStatus("RETRYING");
        task.setRetryCount(0);
        task.setMaxRetries(3);
        taskRepo.save(task);

        // Also reset the workflow status back to RUNNING
        Workflow wf = workflowRepo.findById(task.getWorkflowId()).orElse(null);
        if (wf != null && "FAILED".equals(wf.getStatus())) {
            wf.setStatus("RUNNING");
            workflowRepo.save(wf);
        }

        saveLog(task.getWorkflowId(), task.getTaskId(), task.getTaskName(),
                "TASK_RETRY_REQUESTED", "Manual retry requested — retries reset");

        log.info("Task {} (ID:{}) reset for retry", task.getTaskName(), taskId);
    }



    // ==================== PAUSE WORKFLOW ====================

    public void pauseWorkflow(Long workflowId) {

        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Workflow not found with ID: " + workflowId));

        if (wf.getIsTerminated()) {
            throw new IllegalStateException("Cannot pause a terminated workflow");
        }

        wf.setPaused(true);
        wf.setStatus("PAUSED");
        workflowRepo.save(wf);

        saveLog(workflowId, null, null, "WORKFLOW_PAUSED", "Workflow paused by user");
        log.info("Workflow {} PAUSED", workflowId);
    }



    // ==================== RESUME WORKFLOW ====================

    public void resumeWorkflow(Long workflowId) {

        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Workflow not found with ID: " + workflowId));

        if (wf.getIsTerminated()) {
            throw new IllegalStateException("Cannot resume a terminated workflow");
        }

        if (!wf.getPaused()) {
            throw new IllegalStateException("Workflow is not paused");
        }

        wf.setPaused(false);
        wf.setStatus("RUNNING");
        workflowRepo.save(wf);

        saveLog(workflowId, null, null, "WORKFLOW_RESUMED", "Workflow resumed by user");
        log.info("Workflow {} RESUMED", workflowId);
    }



    // ==================== TERMINATE WORKFLOW ====================

    public void terminateWorkflow(Long workflowId) {

        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Workflow not found with ID: " + workflowId));

        wf.setIsTerminated(true);
        wf.setStatus("TERMINATED");
        workflowRepo.save(wf);

        saveLog(workflowId, null, null, "WORKFLOW_TERMINATED", "Workflow terminated by user");
        log.info("Workflow {} TERMINATED", workflowId);
    }



    // ==================== GET TASK STATUS ====================

    public List<Task> getTasks(Long workflowId) {
        return taskRepo.findByWorkflowIdOrderByTaskOrder(workflowId);
    }



    // ==================== GET ALL WORKFLOWS ====================

    public List<Workflow> getAllWorkflows() {
        return workflowRepo.findAll();
    }



    // ==================== GET DEPENDENCIES FOR A WORKFLOW ====================

    public List<Dependency> getDependencies(Long workflowId) {

        List<Task> tasks = taskRepo.findByWorkflowId(workflowId);
        List<Dependency> allDeps = new ArrayList<>();

        for (Task task : tasks) {
            List<Dependency> deps = dependencyRepo.findByTaskId(task.getTaskId());
            allDeps.addAll(deps);
        }

        return allDeps;
    }



    // ==================== GET EXECUTION LOGS ====================

    public List<ExecutionLog> getLogs(Long workflowId) {
        return logRepo.findByWorkflowIdOrderByTimestampDesc(workflowId);
    }



    // ==================== SAVE EXECUTION LOG ====================

    private void saveLog(Long workflowId, Long taskId, String taskName,
                         String action, String message) {
        try {
            logRepo.save(new ExecutionLog(
                    null, workflowId, taskId, taskName,
                    action, message, LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to save execution log: {}", e.getMessage());
        }
    }

}
const apiBase = "/api/workflow";

const sampleWorkflowDefinition = {
    workflowName: "Order Processing",
    description:
        "E-commerce order orchestration for payment, manual approval, inventory, shipping, and customer notification.",
    tasks: [
        {
            name: "Payment",
            type: "HTTP",
            dependsOn: null,
            purpose: "Charge or authorize payment before downstream fulfillment begins."
        },
        {
            name: "ManualApproval",
            type: "HUMAN",
            dependsOn: "Payment",
            purpose: "Pause high-value or risky orders until an operator approves them."
        },
        {
            name: "Inventory",
            type: "HTTP",
            dependsOn: "ManualApproval",
            purpose: "Reserve stock and confirm item availability."
        },
        {
            name: "Notification",
            type: "HTTP",
            dependsOn: "Payment",
            purpose: "Notify the customer while fulfillment continues."
        },
        {
            name: "Shipping",
            type: "HTTP",
            dependsOn: "Inventory",
            purpose: "Create shipment labels and dispatch the order."
        }
    ]
};

const sampleWorkflowYaml = `workflowName: Order Processing
description: E-commerce order orchestration for payment, manual approval, inventory, shipping, and customer notification.
tasks:
  - name: Payment
    type: HTTP
    purpose: Charge or authorize payment before downstream fulfillment begins.
  - name: ManualApproval
    type: HUMAN
    dependsOn: Payment
    purpose: Pause high-value or risky orders until an operator approves them.
  - name: Inventory
    type: HTTP
    dependsOn: ManualApproval
    purpose: Reserve stock and confirm item availability.
  - name: Notification
    type: HTTP
    dependsOn: Payment
    purpose: Notify the customer while fulfillment continues.
  - name: Shipping
    type: HTTP
    dependsOn: Inventory
    purpose: Create shipment labels and dispatch the order.`;

const state = {
    workflows: [],
    selectedWorkflowId: null,
    tasks: [],
    definitionFormat: "json"
};

const els = {
    startWorkflowButton: document.getElementById("startWorkflowButton"),
    refreshButton: document.getElementById("refreshButton"),
    executeWorkflowButton: document.getElementById("executeWorkflowButton"),
    pauseWorkflowButton: document.getElementById("pauseWorkflowButton"),
    resumeWorkflowButton: document.getElementById("resumeWorkflowButton"),
    terminateWorkflowButton: document.getElementById("terminateWorkflowButton"),
    detailTitle: document.getElementById("detailTitle"),
    selectedWorkflowState: document.getElementById("selectedWorkflowState"),
    workflowTableBody: document.getElementById("workflowTableBody"),
    taskTableBody: document.getElementById("taskTableBody"),
    dependencyList: document.getElementById("dependencyList"),
    logList: document.getElementById("logList"),
    runningCount: document.getElementById("runningCount"),
    completedCount: document.getElementById("completedCount"),
    failedCount: document.getElementById("failedCount"),
    pausedCount: document.getElementById("pausedCount"),
    totalWorkflowCount: document.getElementById("totalWorkflowCount"),
    totalTaskCount: document.getElementById("totalTaskCount"),
    waitingApprovalCount: document.getElementById("waitingApprovalCount"),
    retryingCount: document.getElementById("retryingCount"),
    terminatedCount: document.getElementById("terminatedCount"),
    workflowDefinitionCode: document.getElementById("workflowDefinitionCode"),
    toggleDefinitionFormatButton: document.getElementById("toggleDefinitionFormatButton"),
    copyDefinitionButton: document.getElementById("copyDefinitionButton"),
    copyDefinitionButtonInline: document.getElementById("copyDefinitionButtonInline"),
    dagView: document.getElementById("dagView"),
    workerList: document.getElementById("workerList"),
    controlList: document.getElementById("controlList"),
    edgeCaseList: document.getElementById("edgeCaseList"),
    toast: document.getElementById("toast")
};

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function showToast(message) {
    els.toast.textContent = message;
    els.toast.classList.add("show");
    window.setTimeout(() => els.toast.classList.remove("show"), 2400);
}

async function apiRequest(path, options = {}) {
    const response = await fetch(`${apiBase}${path}`, {
        method: options.method || "GET",
        headers: {
            "Content-Type": "application/json"
        }
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
        const message = data && data.message ? data.message : "Request failed";
        throw new Error(message);
    }

    return data;
}

function statusChip(status) {
    const value = (status || "UNKNOWN").toUpperCase();
    return `<span class="chip ${value}">${escapeHtml(value.replaceAll("_", " "))}</span>`;
}

function renderDefinition() {
    if (!els.workflowDefinitionCode) {
        return;
    }

    els.workflowDefinitionCode.textContent =
        state.definitionFormat === "json"
            ? JSON.stringify(sampleWorkflowDefinition, null, 2)
            : sampleWorkflowYaml;

    if (els.toggleDefinitionFormatButton) {
        els.toggleDefinitionFormatButton.textContent =
            state.definitionFormat === "json" ? "View YAML" : "View JSON";
    }
}

function renderDag() {
    if (!els.dagView) {
        return;
    }

    const nodes = sampleWorkflowDefinition.tasks;
    const entryPoints = nodes.filter((task) => !task.dependsOn);

    els.dagView.innerHTML = `
        <div class="dag-banner">
            <span class="dag-banner-label">Start</span>
            <strong>Order received</strong>
            <p>Execution begins when a new order workflow is created.</p>
        </div>
        <div class="dag-grid">
            ${nodes
                .map((task, index) => {
                    const dependency = task.dependsOn || "Start order";
                    const isBranch = task.name === "Notification";
                    const branchNote = isBranch ? "Parallel branch after payment" : "";
                    return `
                        <article class="dag-card ${isBranch ? "dag-card-branch" : ""}">
                            <div class="dag-card-head">
                                <span class="dag-index">${index + 1}</span>
                                <span class="dag-type">${escapeHtml(task.type)}</span>
                            </div>
                            <h3>${escapeHtml(task.name)}</h3>
                            <p>${escapeHtml(task.purpose)}</p>
                            <div class="dag-meta">Depends on: <strong>${escapeHtml(dependency)}</strong></div>
                            ${branchNote ? `<div class="dag-branch-note">${escapeHtml(branchNote)}</div>` : ""}
                        </article>
                    `;
                })
                .join("")}
        </div>
        <div class="dag-footer">
            <span>${entryPoints.length} entry point${entryPoints.length === 1 ? "" : "s"}</span>
            <span>Parallel branch: Payment → Notification while fulfillment continues</span>
            <span>Human approval gate: ManualApproval</span>
        </div>
    `;
}

function renderWorkerServices() {
    if (!els.workerList) {
        return;
    }

    els.workerList.innerHTML = [
        ["Payment service", "HTTP worker that authorizes or captures payment."],
        ["Inventory service", "HTTP worker that reserves stock and checks availability."],
        ["Shipping service", "HTTP worker that creates dispatch and shipping labels."],
        ["Notification service", "HTTP worker that sends confirmation to the customer."],
        ["Human approval task", "Built-in manual step for high-value or risky orders."],
        ["Custom task workers", "Additional workers can be plugged in without changing the orchestrator core."]
    ]
        .map(([title, description]) => `<li><strong>${escapeHtml(title)}</strong><span>${escapeHtml(description)}</span></li>`)
        .join("");
}

function renderControls() {
    if (!els.controlList) {
        return;
    }

    els.controlList.innerHTML = [
        ["Start", "Create a new workflow instance from the workflow-as-code definition."],
        ["Execute", "Run all tasks that are ready based on DAG dependencies."],
        ["Pause / Resume", "Stop a running order and continue it later without losing state."],
        ["Retry", "Requeue a failed task without replaying the whole workflow."],
        ["Terminate", "Stop a workflow when the order must be cancelled."],
        ["Refresh", "Pull the latest workflow, task, dependency, and log state from the backend."]
    ]
        .map(([title, description]) => `<li><strong>${escapeHtml(title)}</strong><span>${escapeHtml(description)}</span></li>`)
        .join("");
}

function renderEdgeCases() {
    if (!els.edgeCaseList) {
        return;
    }

    els.edgeCaseList.innerHTML = [
        ["Payment failure", "Retry the payment task or mark the order as failed after max retries."],
        ["Out of stock", "Stop fulfillment and surface an exception or back-order path."],
        ["High-value order", "Route the workflow to a human approval gate before inventory and shipping."],
        ["Parallel notification", "Send customer confirmation while fulfillment continues independently."],
        ["Task worker outage", "Use retries and logs to diagnose the failure before resuming."],
        ["Event-driven extension", "Connect queues or webhooks for external services and custom handlers."]
    ]
        .map(([title, description]) => `<li><strong>${escapeHtml(title)}</strong><span>${escapeHtml(description)}</span></li>`)
        .join("");
}

function updateSummary() {
    const workflowCounts = state.workflows.reduce((acc, wf) => {
        const key = (wf.status || "").toUpperCase();
        acc[key] = (acc[key] || 0) + 1;
        return acc;
    }, {});

    const taskCounts = state.tasks.reduce((acc, task) => {
        const key = (task.status || "").toUpperCase();
        acc[key] = (acc[key] || 0) + 1;
        return acc;
    }, {});

    els.runningCount.textContent = workflowCounts.RUNNING || 0;
    els.completedCount.textContent = workflowCounts.COMPLETED || 0;
    els.failedCount.textContent = workflowCounts.FAILED || 0;
    els.pausedCount.textContent = workflowCounts.PAUSED || 0;
    els.totalWorkflowCount.textContent = state.workflows.length;
    els.totalTaskCount.textContent = state.tasks.length;
    els.waitingApprovalCount.textContent = taskCounts.WAITING_APPROVAL || 0;
    els.retryingCount.textContent = (taskCounts.RETRYING || 0) + (taskCounts.FAILED || 0);
    els.terminatedCount.textContent = workflowCounts.TERMINATED || 0;
}

function renderWorkflows() {
    if (state.workflows.length === 0) {
        els.workflowTableBody.innerHTML = "<tr><td colspan=\"4\">No workflows found.</td></tr>";
        return;
    }

    els.workflowTableBody.innerHTML = state.workflows
        .sort((a, b) => b.workflowId - a.workflowId)
        .map((wf) => {
            const selected = state.selectedWorkflowId === wf.workflowId ? " data-selected=\"1\"" : "";
            const status = escapeHtml(wf.status || "UNKNOWN");
            return `
                <tr${selected}>
                    <td>#${wf.workflowId}</td>
                    <td>${escapeHtml(wf.workflowName || "Workflow")}</td>
                    <td>${statusChip(status)}</td>
                    <td>
                        <div class="row-actions">
                            <button class="btn btn-subtle" data-view-workflow="${wf.workflowId}">View</button>
                            <button class="btn btn-subtle" data-execute-workflow="${wf.workflowId}">Execute</button>
                        </div>
                    </td>
                </tr>
            `;
        })
        .join("");
}

function renderTasks() {
    if (!state.selectedWorkflowId) {
        els.taskTableBody.innerHTML = "<tr><td colspan=\"5\">No workflow selected.</td></tr>";
        return;
    }

    if (state.tasks.length === 0) {
        els.taskTableBody.innerHTML = "<tr><td colspan=\"5\">No tasks found.</td></tr>";
        return;
    }

    els.taskTableBody.innerHTML = state.tasks
        .sort((a, b) => (a.taskOrder || 0) - (b.taskOrder || 0))
        .map((task) => {
            let taskAction = "-";

            if ((task.status || "").toUpperCase() === "WAITING_APPROVAL") {
                taskAction = `<button class=\"btn btn-subtle\" data-approve-task=\"${task.taskId}\">Approve</button>`;
            } else if ((task.status || "").toUpperCase() === "FAILED") {
                taskAction = `<button class=\"btn btn-subtle\" data-retry-task=\"${task.taskId}\">Retry</button>`;
            }

            return `
                <tr>
                    <td>${escapeHtml(task.taskName)}</td>
                    <td>${escapeHtml(task.taskType)}</td>
                    <td>${statusChip(task.status)}</td>
                    <td>${escapeHtml(task.retryCount)}/${escapeHtml(task.maxRetries)}</td>
                    <td>${taskAction}</td>
                </tr>
            `;
        })
        .join("");
}

function renderDependencies(dependencies) {
    if (!dependencies || dependencies.length === 0) {
        els.dependencyList.innerHTML = "<li>No dependencies found.</li>";
        return;
    }

    const taskById = new Map(state.tasks.map((task) => [task.taskId, task.taskName]));
    els.dependencyList.innerHTML = dependencies
        .map((dep) => {
            const taskName = taskById.get(dep.taskId) || `Task ${dep.taskId}`;
            const parentName = taskById.get(dep.dependsOnTaskId) || `Task ${dep.dependsOnTaskId}`;
            return `<li><strong>${escapeHtml(taskName)}</strong> depends on <strong>${escapeHtml(parentName)}</strong></li>`;
        })
        .join("");
}

function renderLogs(logs) {
    if (!logs || logs.length === 0) {
        els.logList.innerHTML = "<li>No logs found.</li>";
        return;
    }

    els.logList.innerHTML = logs
        .slice(0, 12)
        .map((log) => `
            <li>
                <div><strong>${escapeHtml(log.action)}</strong> - ${escapeHtml(log.message)}</div>
                <div class="log-meta">${escapeHtml(log.taskName || "Workflow")} | ${escapeHtml(log.timestamp || "")}</div>
            </li>
        `)
        .join("");
}

function applySelectedWorkflowUI() {
    const wf = state.workflows.find((item) => item.workflowId === state.selectedWorkflowId);

    if (!wf) {
        els.detailTitle.textContent = "Workflow Details";
        els.selectedWorkflowState.textContent = "Select a workflow to view details.";
        els.executeWorkflowButton.disabled = true;
        els.pauseWorkflowButton.disabled = true;
        els.resumeWorkflowButton.disabled = true;
        els.terminateWorkflowButton.disabled = true;
        return;
    }

    const status = (wf.status || "").toUpperCase();
    const paused = Boolean(wf.paused);
    const terminated = Boolean(wf.isTerminated) || status === "TERMINATED";
    const completionRate = state.tasks.length
        ? Math.round(
              (state.tasks.filter((task) => (task.status || "").toUpperCase() === "COMPLETED").length / state.tasks.length) *
                  100
          )
        : 0;

    els.detailTitle.textContent = `Workflow #${wf.workflowId} Details`;
    els.selectedWorkflowState.innerHTML = `Current Status: ${statusChip(wf.status)} <span class="selected-subtext">${escapeHtml(
        wf.workflowName || "Order processing workflow"
    )} | ${completionRate}% task completion | ${paused ? "Paused" : "Active"}</span>`;

    els.executeWorkflowButton.disabled = terminated;
    els.pauseWorkflowButton.disabled = terminated || paused;
    els.resumeWorkflowButton.disabled = terminated || !paused;
    els.terminateWorkflowButton.disabled = terminated;
}

async function refreshWorkflows(keepSelection = true) {
    const workflows = await apiRequest("/all");
    state.workflows = Array.isArray(workflows) ? workflows : [];

    if (!keepSelection || !state.workflows.some((wf) => wf.workflowId === state.selectedWorkflowId)) {
        state.selectedWorkflowId = state.workflows[0] ? state.workflows[0].workflowId : null;
    }

    updateSummary();
    renderWorkflows();
    applySelectedWorkflowUI();

    if (state.selectedWorkflowId) {
        await refreshDetail(state.selectedWorkflowId);
    } else {
        state.tasks = [];
        renderTasks();
        renderDependencies([]);
        renderLogs([]);
        updateSummary();
    }
}

async function refreshDetail(workflowId) {
    state.selectedWorkflowId = workflowId;
    const [tasks, dependencies, logs] = await Promise.all([
        apiRequest(`/status/${workflowId}`),
        apiRequest(`/${workflowId}/dependencies`),
        apiRequest(`/${workflowId}/logs`)
    ]);

    state.tasks = Array.isArray(tasks) ? tasks : [];
    renderTasks();
    renderDependencies(Array.isArray(dependencies) ? dependencies : []);
    renderLogs(Array.isArray(logs) ? logs : []);
    renderWorkflows();
    updateSummary();
    applySelectedWorkflowUI();
}

async function runWorkflowAction(action, workflowId, successMessage) {
    await apiRequest(`/${action}/${workflowId}`, { method: "POST" });
    showToast(successMessage);
    await refreshWorkflows(true);
    await refreshDetail(workflowId);
}

async function runTaskAction(action, taskId, successMessage) {
    await apiRequest(`/${action}/${taskId}`, { method: "POST" });
    showToast(successMessage);
    await refreshWorkflows(true);
    if (state.selectedWorkflowId) {
        await refreshDetail(state.selectedWorkflowId);
    }
}

async function copyDefinitionToClipboard() {
    const content = state.definitionFormat === "json" ? JSON.stringify(sampleWorkflowDefinition, null, 2) : sampleWorkflowYaml;
    await navigator.clipboard.writeText(content);
    showToast("Workflow definition copied");
}

function bindEvents() {
    if (els.startWorkflowButton) {
        els.startWorkflowButton.addEventListener("click", async () => {
        try {
            const wf = await apiRequest("/start", { method: "POST" });
            showToast("Workflow started");
            await refreshWorkflows(false);
            if (wf && wf.workflowId) {
                await refreshDetail(wf.workflowId);
            }
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.refreshButton) {
        els.refreshButton.addEventListener("click", async () => {
        try {
            await refreshWorkflows(true);
            showToast("Dashboard refreshed");
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.toggleDefinitionFormatButton) {
        els.toggleDefinitionFormatButton.addEventListener("click", async () => {
        state.definitionFormat = state.definitionFormat === "json" ? "yaml" : "json";
        renderDefinition();
        showToast(`Showing ${state.definitionFormat.toUpperCase()} definition`);
    });
    }

    const copyButtons = [els.copyDefinitionButton, els.copyDefinitionButtonInline].filter(Boolean);
    copyButtons.forEach((button) => {
        button.addEventListener("click", async () => {
            try {
                await copyDefinitionToClipboard();
            } catch (error) {
                showToast(error.message || "Copy failed");
            }
        });
    });

    if (els.executeWorkflowButton) {
        els.executeWorkflowButton.addEventListener("click", async () => {
        if (!state.selectedWorkflowId) return;
        try {
            await runWorkflowAction("execute", state.selectedWorkflowId, "Execution triggered");
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.pauseWorkflowButton) {
        els.pauseWorkflowButton.addEventListener("click", async () => {
        if (!state.selectedWorkflowId) return;
        try {
            await runWorkflowAction("pause", state.selectedWorkflowId, "Workflow paused");
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.resumeWorkflowButton) {
        els.resumeWorkflowButton.addEventListener("click", async () => {
        if (!state.selectedWorkflowId) return;
        try {
            await runWorkflowAction("resume", state.selectedWorkflowId, "Workflow resumed");
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.terminateWorkflowButton) {
        els.terminateWorkflowButton.addEventListener("click", async () => {
        if (!state.selectedWorkflowId) return;
        try {
            await runWorkflowAction("terminate", state.selectedWorkflowId, "Workflow terminated");
        } catch (error) {
            showToast(error.message);
        }
    });
    }

    if (els.workflowTableBody) {
        els.workflowTableBody.addEventListener("click", async (event) => {
        const viewId = event.target.getAttribute("data-view-workflow");
        const executeId = event.target.getAttribute("data-execute-workflow");

        if (viewId) {
            try {
                await refreshDetail(Number(viewId));
            } catch (error) {
                showToast(error.message);
            }
            return;
        }

        if (executeId) {
            try {
                await runWorkflowAction("execute", Number(executeId), "Execution triggered");
            } catch (error) {
                showToast(error.message);
            }
        }
    });
    }

    if (els.taskTableBody) {
        els.taskTableBody.addEventListener("click", async (event) => {
        const approveId = event.target.getAttribute("data-approve-task");
        const retryId = event.target.getAttribute("data-retry-task");

        if (approveId) {
            try {
                await runTaskAction("approve", Number(approveId), "Task approved");
            } catch (error) {
                showToast(error.message);
            }
            return;
        }

        if (retryId) {
            try {
                await runTaskAction("retry", Number(retryId), "Task queued for retry");
            } catch (error) {
                showToast(error.message);
            }
        }
    });
    }
}

async function boot() {
    renderDefinition();
    renderDag();
    renderWorkerServices();
    renderControls();
    renderEdgeCases();
    bindEvents();
    try {
        await refreshWorkflows(false);
    } catch (error) {
        showToast(error.message || "Failed to load dashboard");
    }
}

boot();

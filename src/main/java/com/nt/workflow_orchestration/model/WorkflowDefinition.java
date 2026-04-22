package com.nt.workflow_orchestration.model;

import java.util.List;

public class WorkflowDefinition {

   private String workflowName;

   private List<TaskDefinition> tasks;

   public String getWorkflowName() {
      return workflowName;
   }

   public void setWorkflowName(String workflowName) {
      this.workflowName = workflowName;
   }

   public List<TaskDefinition> getTasks() {
      return tasks;
   }

   public void setTasks(List<TaskDefinition> tasks) {
      this.tasks = tasks;
   }
}
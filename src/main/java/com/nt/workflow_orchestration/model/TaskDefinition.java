package com.nt.workflow_orchestration.model;


public class TaskDefinition {

   private String name;

   private String dependsOn;

   private String type;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name=name;
   }

   public String getDependsOn() {
      return dependsOn;
   }

   public void setDependsOn(String dependsOn) {
      this.dependsOn=dependsOn;
   }

   public String getType() {
 return type;
}

public void setType(String type) {
 this.type = type;
}

}
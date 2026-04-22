# Workflow Orchestration System 
A Spring Boot–based Workflow Orchestration System that allows users to define, manage, 
and execute workflows with task dependencies. 
Features: - Create and execute workflows - Task dependency management - Execution logging & tracking - REST API support - Built-in frontend 

# Tech Stack: 
Backend: Java, Spring Boot 
Frontend: HTML, CSS, JavaScript 
Database: H2/MySQL 
Build Tool: Maven 

# Workflow Orchestration System – Working (Points)
Define a workflow with multiple tasks and their dependencies (via JSON or database)
User triggers workflow execution through frontend or API
Request is received by the Controller layer
Controller forwards the request to the Service layer
Service fetches workflow details (tasks + dependencies)
System resolves dependencies using a DAG/topological approach
Determines the correct execution order of tasks
Executes tasks sequentially via worker APIs
Logs each task’s execution status (SUCCESS/FAILED)
Handles errors using a global exception handler
Sends final execution result back to the user interface

# Setup: 
1. Clone repo 
2. Run: ./mvnw spring-boot:run 
3. Open: http://localhost:8080/index.html 

# API Endpoints: 
POST /workflow/create 
GET /workflow/{id} 
POST /workflow/execute/{id} 
How it Works: - Define workflow - Resolve dependencies - Execute tasks in order - Log execution 

# Author: 
- Shinjini Sarkar
- Kavya R Naik
- Gayatri Patil
- Arshia B

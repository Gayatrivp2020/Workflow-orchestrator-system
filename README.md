<img width="218" height="25" alt="image" src="https://github.com/user-attachments/assets/34e238df-ae16-4880-b3f3-1b11cc383df7" /># Workflow Orchestration System 
A Spring Boot–based Workflow Orchestration System that allows users to define, manage, 
and execute workflows with task dependencies. 
Features: - Create and execute workflows - Task dependency management - Execution logging & tracking - REST API support - Built-in frontend 

# Tech Stack: 
Backend: Java, Spring Boot 
Frontend: HTML, CSS, JavaScript 
Database: H2/MySQL 
Build Tool: Maven 

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

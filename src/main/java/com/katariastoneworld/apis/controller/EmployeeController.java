package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.EmployeeRequestDTO;
import com.katariastoneworld.apis.dto.EmployeeResponseDTO;
import com.katariastoneworld.apis.service.EmployeeService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = {"/api/employees","employees"}, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Employees", description = "Employee management endpoints. All endpoints require admin role.")
public class EmployeeController {
    
    @Autowired
    private EmployeeService employeeService;
    
    @PostMapping(consumes = "application/json")
    public ResponseEntity<EmployeeResponseDTO> createEmployee(@Valid @RequestBody EmployeeRequestDTO requestDTO, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        EmployeeResponseDTO response = employeeService.createEmployee(requestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<EmployeeResponseDTO>> getAllEmployees(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<EmployeeResponseDTO> employees = employeeService.getAllEmployees(location);
        return new ResponseEntity<>(employees, HttpStatus.OK);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            EmployeeResponseDTO employee = employeeService.getEmployeeById(id, location);
            return ResponseEntity.ok(employee);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO requestDTO, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            EmployeeResponseDTO response = employeeService.updateEmployee(id, requestDTO, location);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            employeeService.deleteEmployee(id, location);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}


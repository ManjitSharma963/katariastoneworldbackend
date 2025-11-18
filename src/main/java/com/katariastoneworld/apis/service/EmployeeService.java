package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.EmployeeRequestDTO;
import com.katariastoneworld.apis.dto.EmployeeResponseDTO;
import com.katariastoneworld.apis.entity.Employee;
import com.katariastoneworld.apis.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO requestDTO, String location) {
        Employee employee = new Employee();
        employee.setEmployeeName(requestDTO.getEmployeeName());
        employee.setSalaryAmount(requestDTO.getSalaryAmount());
        employee.setJoiningDate(requestDTO.getJoiningDate());
        employee.setLocation(location);
        
        Employee savedEmployee = employeeRepository.save(employee);
        return convertToResponseDTO(savedEmployee);
    }
    
    public List<EmployeeResponseDTO> getAllEmployees(String location) {
        return employeeRepository.findByLocation(location).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public EmployeeResponseDTO getEmployeeById(Long id, String location) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(employee.getLocation())) {
            throw new RuntimeException("Employee not found with id: " + id);
        }
        
        return convertToResponseDTO(employee);
    }
    
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO, String location) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(employee.getLocation())) {
            throw new RuntimeException("Employee not found with id: " + id);
        }
        
        employee.setEmployeeName(requestDTO.getEmployeeName());
        employee.setSalaryAmount(requestDTO.getSalaryAmount());
        employee.setJoiningDate(requestDTO.getJoiningDate());
        
        Employee updatedEmployee = employeeRepository.save(employee);
        return convertToResponseDTO(updatedEmployee);
    }
    
    public void deleteEmployee(Long id, String location) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(employee.getLocation())) {
            throw new RuntimeException("Employee not found with id: " + id);
        }
        
        employeeRepository.deleteById(id);
    }
    
    private EmployeeResponseDTO convertToResponseDTO(Employee employee) {
        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        dto.setId(employee.getId());
        dto.setEmployeeName(employee.getEmployeeName());
        dto.setSalaryAmount(employee.getSalaryAmount());
        dto.setJoiningDate(employee.getJoiningDate());
        dto.setCreatedAt(employee.getCreatedAt());
        dto.setUpdatedAt(employee.getUpdatedAt());
        return dto;
    }
}


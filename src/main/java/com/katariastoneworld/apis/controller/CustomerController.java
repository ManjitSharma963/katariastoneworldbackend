package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.CustomerRequestDTO;
import com.katariastoneworld.apis.dto.CustomerResponseDTO;
import com.katariastoneworld.apis.service.CustomerService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    
    @Autowired
    private CustomerService customerService;
    
    @PostMapping
    @RequiresRole({"user", "admin"})
    public ResponseEntity<CustomerResponseDTO> createCustomer(
            @Valid @RequestBody CustomerRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        CustomerResponseDTO response = customerService.createCustomer(requestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<CustomerResponseDTO>> getAllCustomers(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<CustomerResponseDTO> customers = customerService.getAllCustomers(location);
        return ResponseEntity.ok(customers);
    }
    
    @GetMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<CustomerResponseDTO> getCustomerById(@PathVariable Long id) {
        CustomerResponseDTO customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(customer);
    }
    
    @GetMapping("/phone/{phone}")
    @RequiresRole("admin")
    public ResponseEntity<CustomerResponseDTO> getCustomerByPhone(@PathVariable String phone) {
        com.katariastoneworld.apis.entity.Customer customer = customerService.getCustomerByPhone(phone);
        CustomerResponseDTO response = customerService.convertToResponseDTO(customer);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<CustomerResponseDTO> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequestDTO requestDTO) {
        CustomerResponseDTO response = customerService.updateCustomer(id, requestDTO);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}


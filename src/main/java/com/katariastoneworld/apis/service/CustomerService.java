package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CustomerRequestDTO;
import com.katariastoneworld.apis.dto.CustomerResponseDTO;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CustomerService {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    public Customer getOrCreateCustomer(String phone) {
        return customerRepository.findByPhone(phone)
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setPhone(phone);
                    return customerRepository.save(customer);
                });
    }
    
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin) {
        return getOrCreateCustomer(phone, customerName, address, gstin, null, null);
    }
    
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin, String email) {
        return getOrCreateCustomer(phone, customerName, address, gstin, email, null);
    }
    
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin, String email, String location) {
        return customerRepository.findByPhone(phone)
                .map(customer -> {
                    // Update existing customer with new details if provided
                    boolean updated = false;
                    if (customerName != null && !customerName.trim().isEmpty()) {
                        customer.setCustomerName(customerName);
                        updated = true;
                    }
                    if (address != null && !address.trim().isEmpty()) {
                        customer.setAddress(address);
                        updated = true;
                    }
                    if (gstin != null && !gstin.trim().isEmpty()) {
                        customer.setGstin(gstin);
                        updated = true;
                    }
                    if (email != null && !email.trim().isEmpty()) {
                        customer.setEmail(email);
                        updated = true;
                    }
                    if (location != null && !location.trim().isEmpty()) {
                        customer.setLocation(location);
                        updated = true;
                    }
                    if (updated) {
                        return customerRepository.save(customer);
                    }
                    return customer;
                })
                .orElseGet(() -> {
                    // Create new customer with provided details
                    Customer customer = new Customer();
                    customer.setPhone(phone);
                    if (customerName != null && !customerName.trim().isEmpty()) {
                        customer.setCustomerName(customerName);
                    }
                    if (address != null && !address.trim().isEmpty()) {
                        customer.setAddress(address);
                    }
                    if (gstin != null && !gstin.trim().isEmpty()) {
                        customer.setGstin(gstin);
                    }
                    if (email != null && !email.trim().isEmpty()) {
                        customer.setEmail(email);
                    }
                    if (location != null && !location.trim().isEmpty()) {
                        customer.setLocation(location);
                    }
                    return customerRepository.save(customer);
                });
    }
    
    public Customer getCustomerByPhone(String phone) {
        return customerRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Customer not found with phone: " + phone));
    }
    
    public List<CustomerResponseDTO> getAllCustomers(String location) {
        List<Customer> customers;
        if (location != null && !location.trim().isEmpty()) {
            customers = customerRepository.findByLocation(location);
        } else {
            customers = customerRepository.findAll();
        }
        return customers.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public CustomerResponseDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        return convertToResponseDTO(customer);
    }
    
    public CustomerResponseDTO createCustomer(CustomerRequestDTO requestDTO, String location) {
        Customer customer = new Customer();
        customer.setPhone(requestDTO.getPhone());
        customer.setName(requestDTO.getName());
        customer.setCustomerName(requestDTO.getCustomerName());
        customer.setAddress(requestDTO.getAddress());
        customer.setGstin(requestDTO.getGstin());
        customer.setEmail(requestDTO.getEmail());
        customer.setLocation(requestDTO.getLocation() != null ? requestDTO.getLocation() : location);
        
        Customer savedCustomer = customerRepository.save(customer);
        return convertToResponseDTO(savedCustomer);
    }
    
    public CustomerResponseDTO updateCustomer(Long id, CustomerRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        
        if (requestDTO.getPhone() != null && !requestDTO.getPhone().trim().isEmpty()) {
            customer.setPhone(requestDTO.getPhone());
        }
        if (requestDTO.getName() != null) {
            customer.setName(requestDTO.getName());
        }
        if (requestDTO.getCustomerName() != null) {
            customer.setCustomerName(requestDTO.getCustomerName());
        }
        if (requestDTO.getAddress() != null) {
            customer.setAddress(requestDTO.getAddress());
        }
        if (requestDTO.getGstin() != null) {
            customer.setGstin(requestDTO.getGstin());
        }
        if (requestDTO.getEmail() != null) {
            customer.setEmail(requestDTO.getEmail());
        }
        if (requestDTO.getLocation() != null) {
            customer.setLocation(requestDTO.getLocation());
        }
        
        Customer updatedCustomer = customerRepository.save(customer);
        return convertToResponseDTO(updatedCustomer);
    }
    
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new RuntimeException("Customer not found with id: " + id);
        }
        customerRepository.deleteById(id);
    }
    
    public CustomerResponseDTO convertToResponseDTO(Customer customer) {
        CustomerResponseDTO dto = new CustomerResponseDTO();
        dto.setId(customer.getId());
        dto.setPhone(customer.getPhone());
        dto.setName(customer.getName());
        dto.setCustomerName(customer.getCustomerName());
        dto.setAddress(customer.getAddress());
        dto.setGstin(customer.getGstin());
        dto.setEmail(customer.getEmail());
        dto.setLocation(customer.getLocation());
        dto.setCreatedAt(customer.getCreatedAt());
        dto.setUpdatedAt(customer.getUpdatedAt());
        return dto;
    }
}


package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CustomerRequestDTO;
import com.katariastoneworld.apis.dto.CustomerResponseDTO;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
        return getOrCreateCustomer(phone, customerName, address, gstin, email, null, null);
    }
    
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin, String email, String location) {
        return getOrCreateCustomer(phone, customerName, address, gstin, email, location, null);
    }
    
    /**
     * Get or create customer for bills. Works with existing DB that enforces UNIQUE(phone) (or similar)
     * without requiring schema changes: always reuse the row for that phone if it exists.
     * Location is updated when provided so listing/filtering by location stays meaningful for recent activity.
     */
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin, String email, String location, Long userId) {
        Optional<Customer> existingByPhone = customerRepository.findByPhone(phone);
        if (existingByPhone.isPresent()) {
            return updateCustomerDetails(existingByPhone.get(), customerName, address, gstin, email, location);
        }
        if (userId != null) {
            return customerRepository.findByPhoneAndUserId(phone, userId)
                    .map(c -> updateCustomerDetails(c, customerName, address, gstin, email, location))
                    .orElseGet(() -> createNewCustomer(phone, customerName, address, gstin, email, location, userId));
        }
        return createNewCustomer(phone, customerName, address, gstin, email, location, null);
    }

    private Customer updateCustomerDetails(Customer customer, String customerName, String address, String gstin, String email, String location) {
        boolean updated = false;
        if (customerName != null && !customerName.trim().isEmpty()) { customer.setCustomerName(customerName); updated = true; }
        if (address != null && !address.trim().isEmpty()) { customer.setAddress(address); updated = true; }
        if (gstin != null && !gstin.trim().isEmpty()) { customer.setGstin(gstin); updated = true; }
        if (email != null && !email.trim().isEmpty()) { customer.setEmail(email); updated = true; }
        if (location != null && !location.trim().isEmpty()) { customer.setLocation(location); updated = true; }
        return updated ? customerRepository.save(customer) : customer;
    }

    private Customer createNewCustomer(String phone, String customerName, String address, String gstin, String email, String location, Long userId) {
        Customer customer = new Customer();
        customer.setPhone(phone);
        customer.setUserId(userId);
        if (customerName != null && !customerName.trim().isEmpty()) customer.setCustomerName(customerName);
        if (address != null && !address.trim().isEmpty()) customer.setAddress(address);
        if (gstin != null && !gstin.trim().isEmpty()) customer.setGstin(gstin);
        if (email != null && !email.trim().isEmpty()) customer.setEmail(email);
        if (location != null && !location.trim().isEmpty()) customer.setLocation(location);
        return customerRepository.save(customer);
    }
    
    public Customer getCustomerByPhone(String phone) {
        return customerRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Customer not found with phone: " + phone));
    }
    
    public List<CustomerResponseDTO> getAllCustomers(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("Location is required to fetch customers.");
        }
        List<Customer> customers = customerRepository.findByLocation(location);
        return customers.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public CustomerResponseDTO getCustomerById(Long id, String location) {
        Customer customer = customerRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        return convertToResponseDTO(customer);
    }
    
    public CustomerResponseDTO createCustomer(CustomerRequestDTO requestDTO, String location) {
        String phone = requestDTO.getPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            Optional<Customer> existing = customerRepository.findByPhone(phone.trim());
            if (existing.isPresent()) {
                Customer c = existing.get();
                if (requestDTO.getName() != null) c.setName(requestDTO.getName());
                if (requestDTO.getCustomerName() != null) c.setCustomerName(requestDTO.getCustomerName());
                if (requestDTO.getAddress() != null) c.setAddress(requestDTO.getAddress());
                if (requestDTO.getGstin() != null) c.setGstin(requestDTO.getGstin());
                if (requestDTO.getEmail() != null) c.setEmail(requestDTO.getEmail());
                String loc = requestDTO.getLocation() != null ? requestDTO.getLocation() : location;
                if (loc != null && !loc.trim().isEmpty()) c.setLocation(loc);
                return convertToResponseDTO(customerRepository.save(c));
            }
        }
        Customer customer = new Customer();
        customer.setPhone(phone);
        customer.setName(requestDTO.getName());
        customer.setCustomerName(requestDTO.getCustomerName());
        customer.setAddress(requestDTO.getAddress());
        customer.setGstin(requestDTO.getGstin());
        customer.setEmail(requestDTO.getEmail());
        customer.setLocation(requestDTO.getLocation() != null ? requestDTO.getLocation() : location);
        Customer savedCustomer = customerRepository.save(customer);
        return convertToResponseDTO(savedCustomer);
    }
    
    public CustomerResponseDTO updateCustomer(Long id, CustomerRequestDTO requestDTO, String location) {
        Customer customer = customerRepository.findByIdAndLocation(id, location)
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
    
    public void deleteCustomer(Long id, String location) {
        Customer customer = customerRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
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


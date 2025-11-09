package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return getOrCreateCustomer(phone, customerName, address, gstin, null);
    }
    
    public Customer getOrCreateCustomer(String phone, String customerName, String address, String gstin, String email) {
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
                    return customerRepository.save(customer);
                });
    }
    
    public Customer getCustomerByPhone(String phone) {
        return customerRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Customer not found with phone: " + phone));
    }
}


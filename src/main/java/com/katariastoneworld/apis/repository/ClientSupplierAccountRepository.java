package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ClientAccountChannel;
import com.katariastoneworld.apis.entity.ClientSupplierAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientSupplierAccountRepository extends JpaRepository<ClientSupplierAccount, Long> {

    Optional<ClientSupplierAccount> findByLocationAndClientKey(String location, String clientKey);

    Optional<ClientSupplierAccount> findByLocationAndClientKeyAndAccountChannel(
            String location, String clientKey, ClientAccountChannel accountChannel);

    List<ClientSupplierAccount> findByLocationOrderByDisplayNameAscClientKeyAsc(String location);
}

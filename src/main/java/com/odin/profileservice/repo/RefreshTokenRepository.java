package com.odin.profileservice.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.odin.profileservice.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByRefreshTokenAndIsActiveTrue(String refreshToken);

    long deleteByCustomerId(Long customerId);

	Optional<RefreshToken> findByRefreshTokenAndDeviceSignatureAndIsActiveTrue(String refreshToken,
			String deviceSignature);

	RefreshToken findByCustomerId(Long valueOf); 

}

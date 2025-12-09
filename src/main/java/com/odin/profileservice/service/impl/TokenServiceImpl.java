package com.odin.profileservice.service.impl;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.LanguageConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.JwtDTO;
import com.odin.profileservice.dto.RefreshRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.RefreshToken;
import com.odin.profileservice.repo.RefreshTokenRepository;
import com.odin.profileservice.service.TokenService;
import com.odin.profileservice.utility.JwtTokenUtil;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class TokenServiceImpl implements TokenService {
	
	@Value("${jwt.refreshExpiration}")
	private String refreshTokenExpiryMs;

	@Autowired
	private RefreshTokenRepository refreshTokenRepo;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private ResponseObject response;

	@Override
	public ResponseDTO refresh(RefreshRequestDTO request) {
	    if (request == null || request.getRefreshToken() == null || request.getDeviceSignature() == null) {
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }

	    Optional<RefreshToken> opt = refreshTokenRepo
	            .findByRefreshTokenAndDeviceSignatureAndIsActiveTrue(
	                request.getRefreshToken(),
	                request.getDeviceSignature()
	            );

	    if (!opt.isPresent()) {
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }

	    RefreshToken rt = opt.get();
	    Timestamp now = new Timestamp(System.currentTimeMillis());

	    if (rt.getExpiryDate().before(now) || Boolean.FALSE.equals(rt.getIsActive())) {
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }

	    // Generate new tokens
	    String newAccessToken = jwtTokenUtil.generateAccessToken(
	            String.valueOf(rt.getCustomerId()),
	            request.getDeviceSignature()
	    );

	    String newRefreshToken = jwtTokenUtil.generateRefreshToken();
	    
	    // Rotate token in DB
	    rt.setIsActive(false); // old one inactive
	    
	    RefreshToken newRt = new RefreshToken();
	    newRt.setCustomerId(rt.getCustomerId());
	    newRt.setRefreshToken(newRefreshToken);
	    newRt.setDeviceSignature(request.getDeviceSignature());
	    newRt.setCreatedAt(now);
	    newRt.setExpiryDate(new Timestamp(now.getTime() + Long.valueOf(refreshTokenExpiryMs)));
	    newRt.setIsActive(true);

	    refreshTokenRepo.delete(rt);
	    refreshTokenRepo.save(newRt);

	    JwtDTO jwt = JwtDTO.builder()
	            .accessToken(newAccessToken)
	            .refreshToken(newRefreshToken)
	            .deviceSignature(request.getDeviceSignature())
	            .build();

	    return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, jwt);
	}


	@Override
	public ResponseDTO revoke(RefreshRequestDTO request) {
		if (request == null || request.getRefreshToken() == null) {
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
		}
		Optional<RefreshToken> opt = refreshTokenRepo.findByRefreshTokenAndIsActiveTrue(request.getRefreshToken());
		if (!opt.isPresent()) {
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
		}
		RefreshToken rt = opt.get();

		rt.setIsActive(false);
		rt.setRevokedAt(new Timestamp(System.currentTimeMillis()));
		refreshTokenRepo.delete(rt);

		// Build success response
		return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE,
				"Refresh token revoked successfully");
	}

}

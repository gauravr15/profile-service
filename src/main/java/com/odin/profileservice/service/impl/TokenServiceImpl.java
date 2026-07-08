package com.odin.profileservice.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Objects;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
	    if (request == null) {
	        logRefreshReject(RefreshRejectReason.REQUEST_BODY_NULL, null, null, null, null, null, null);
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }
	    if (request.getRefreshToken() == null) {
	        logRefreshReject(RefreshRejectReason.REFRESH_TOKEN_MISSING, request, null, null, null, null, null);
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }
	    if (request.getDeviceSignature() == null) {
	        logRefreshReject(RefreshRejectReason.DEVICE_SIGNATURE_MISSING, request, null, null, null, null, null);
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	    }

	    Optional<RefreshToken> opt;
	    try {
	        opt = refreshTokenRepo
	                .findByRefreshTokenAndDeviceSignatureAndIsActiveTrue(
	                    request.getRefreshToken(),
	                    request.getDeviceSignature()
	                );
	    } catch (RuntimeException e) {
	        log.warn("[BACKEND_REFRESH_REJECT] reason={} tokenFp={} requestedDeviceFp={} rowExists={} deviceMatch={} active={} expired={} exception={}",
	                RefreshRejectReason.DATABASE_EXCEPTION, fingerprint(request.getRefreshToken()),
	                fingerprint(request.getDeviceSignature()), null, null, null, null, e.getClass().getSimpleName());
	        throw e;
	    }

	    Timestamp now = new Timestamp(System.currentTimeMillis());

	    if (!opt.isPresent()) {
	        return rejectWithDiagnosticLookup(request, now);
	    }

	    RefreshToken rt = opt.get();

	    if (rt.getExpiryDate().before(now) || Boolean.FALSE.equals(rt.getIsActive())) {
	        RefreshRejectReason reason = Boolean.FALSE.equals(rt.getIsActive())
	                ? RefreshRejectReason.TOKEN_ROW_FOUND_INACTIVE
	                : RefreshRejectReason.TOKEN_ROW_FOUND_EXPIRED;
	        logRefreshLookupDetail(request, rt, now);
	        logRefreshReject(reason, request, true, true, rt.getIsActive(), isExpired(rt, now), rt.getCustomerId());
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
	    log.info("[BACKEND_REFRESH_ROTATE] customerFp={} deviceFp={} oldTokenFp={} newTokenFp={} result=success",
	            fingerprint(rt.getCustomerId()), fingerprint(request.getDeviceSignature()),
	            fingerprint(request.getRefreshToken()), fingerprint(newRefreshToken));

	    JwtDTO jwt = JwtDTO.builder()
	            .accessToken(newAccessToken)
	            .refreshToken(newRefreshToken)
	            .deviceSignature(request.getDeviceSignature())
	            .build();

	    return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, jwt);
	}

	private ResponseDTO rejectWithDiagnosticLookup(RefreshRequestDTO request, Timestamp now) {
		try {
			Optional<RefreshToken> diagnosticRow = refreshTokenRepo.findByRefreshToken(request.getRefreshToken());
			if (!diagnosticRow.isPresent()) {
				log.info("[BACKEND_REFRESH_LOOKUP_DETAIL] tokenFp={} requestedDeviceFp={} rowExists={} deviceMatch={} active={} expired={} customerFp={}",
						fingerprint(request.getRefreshToken()), fingerprint(request.getDeviceSignature()), false, null, null, null, null);
				logRefreshReject(RefreshRejectReason.TOKEN_ROW_NOT_FOUND, request, false, null, null, null, null);
				return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
			}

			RefreshToken row = diagnosticRow.get();
			Boolean deviceMatch = Objects.equals(row.getDeviceSignature(), request.getDeviceSignature());
			Boolean active = row.getIsActive();
			Boolean expired = isExpired(row, now);
			logRefreshLookupDetail(request, row, now);

			RefreshRejectReason reason = RefreshRejectReason.UNKNOWN_REFRESH_REJECTION;
			if (!Boolean.TRUE.equals(deviceMatch)) {
				reason = RefreshRejectReason.TOKEN_ROW_FOUND_DEVICE_SIGNATURE_MISMATCH;
			} else if (Boolean.FALSE.equals(active)) {
				reason = RefreshRejectReason.TOKEN_ROW_FOUND_INACTIVE;
			} else if (Boolean.TRUE.equals(expired)) {
				reason = RefreshRejectReason.TOKEN_ROW_FOUND_EXPIRED;
			}

			logRefreshReject(reason, request, true, deviceMatch, active, expired, row.getCustomerId());
		} catch (RuntimeException e) {
			log.warn("[BACKEND_REFRESH_REJECT] reason={} tokenFp={} requestedDeviceFp={} rowExists={} deviceMatch={} active={} expired={} exception={} stage=diagnosticLookup",
					RefreshRejectReason.DATABASE_EXCEPTION, fingerprint(request.getRefreshToken()),
					fingerprint(request.getDeviceSignature()), null, null, null, null, e.getClass().getSimpleName());
		}
		return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REFRESH_TOKEN);
	}

	private void logRefreshLookupDetail(RefreshRequestDTO request, RefreshToken row, Timestamp now) {
		log.info("[BACKEND_REFRESH_LOOKUP_DETAIL] tokenFp={} requestedDeviceFp={} rowExists={} storedDeviceFp={} deviceMatch={} active={} expired={} customerFp={}",
				fingerprint(request.getRefreshToken()), fingerprint(request.getDeviceSignature()), true,
				fingerprint(row.getDeviceSignature()), Objects.equals(row.getDeviceSignature(), request.getDeviceSignature()),
				row.getIsActive(), isExpired(row, now), fingerprint(row.getCustomerId()));
	}

	private void logRefreshReject(RefreshRejectReason reason, RefreshRequestDTO request, Boolean rowExists,
			Boolean deviceMatch, Boolean active, Boolean expired, Long customerId) {
		log.info("[BACKEND_REFRESH_REJECT] reason={} tokenFp={} requestedDeviceFp={} rowExists={} deviceMatch={} active={} expired={} customerFp={}",
				reason, fingerprint(request == null ? null : request.getRefreshToken()),
				fingerprint(request == null ? null : request.getDeviceSignature()), rowExists, deviceMatch, active, expired,
				fingerprint(customerId));
	}

	private boolean isExpired(RefreshToken row, Timestamp now) {
		return row.getExpiryDate() != null && row.getExpiryDate().before(now);
	}

	private String fingerprint(Object value) {
		if (value == null) {
			return "null";
		}
		String text = String.valueOf(value);
		if (text.isEmpty()) {
			return "empty";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < 4 && i < hash.length; i++) {
				builder.append(String.format("%02x", hash[i]));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException e) {
			return "sha256-unavailable";
		}
	}

	private enum RefreshRejectReason {
		REQUEST_BODY_NULL,
		REFRESH_TOKEN_MISSING,
		DEVICE_SIGNATURE_MISSING,
		TOKEN_ROW_NOT_FOUND,
		TOKEN_ROW_FOUND_DEVICE_SIGNATURE_MISMATCH,
		TOKEN_ROW_FOUND_INACTIVE,
		TOKEN_ROW_FOUND_EXPIRED,
		DATABASE_EXCEPTION,
		UNKNOWN_REFRESH_REJECTION
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

package com.odin.profileservice.utility;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import com.odin.profileservice.enums.OTPType;

@Component
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public OtpService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    private String buildKey(String mobileNumber, OTPType type) {
        return "OTP:" + type.name() + ":" + mobileNumber;
    }

    public void saveOtp(String mobileNumber, String otp, OTPType type, long ttlMinutes) {
        valueOps.set(buildKey(mobileNumber, type), otp, ttlMinutes, TimeUnit.SECONDS);
    }

    public String getOtp(String mobileNumber, OTPType type) {
        Object otp = valueOps.get(buildKey(mobileNumber, type));
        return otp != null ? otp.toString() : null;
    }

    public boolean validateOtp(String mobileNumber, String otp, OTPType type) {
        String cachedOtp = getOtp(mobileNumber, type);
        return otp != null && otp.equals(cachedOtp);
    }

    public void clearOtp(String mobileNumber, OTPType type) {
        redisTemplate.delete(buildKey(mobileNumber, type));
    }
}

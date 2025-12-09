package com.odin.profileservice.dto;

import com.odin.profileservice.enums.OTPType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OtpRequestDTO {

	
	private String mobile;
	
	private String email;
	
	private OTPType type;
}

package com.odin.profileservice.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthDTO {

	private String customerId;
	private Timestamp creationTimestamp;
	private Timestamp updateTimestamp;
	private Boolean isFirstTimeLogin;
	private Timestamp lastLoginTimestamp;
	private Boolean isTempPassword;
	private Timestamp passwordChangedDate;
	private Integer passwordChangeCount;
	private String password;
	private Boolean isActive;
	private Boolean isDeleted;
	private Boolean isTempLock;
	private Boolean isPermLock;
	private Integer tempLockCount;
	private Integer permLockCount;
	private Timestamp tempLockDate;
	private Timestamp permLockDate;
}

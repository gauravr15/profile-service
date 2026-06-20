package com.odin.profileservice.constants;

public class ResponseCodes {

	
	public static final Integer SUCCESS_CODE = 2000;
	public static final Integer FAILURE_CODE = 1000;
	public static final Integer EXCEPTION_CODE = 1;
	public static final Integer INVALID_REQUEST = 2;
	public static final Integer INTERNAL_SERVER_ERROR = 3;
	public static final Integer FORBIDDEN = 5;
	public static final Integer NOT_FOUND = 6;
	public static final Integer APP_VERSION_MISSING = 4;
	public static final Integer APP_UPDATE_REQUIRED = 998;
	public static final Integer NO_DATA_FOUND = 999;
	public static final Integer INVALID_PASSWORD_FORMAT = 997;
	
	public static final String SUCCESS = "SUCCESS";
	public static final String FAILURE = "FAILURE";
	public static final Integer USER_ALREADY_EXISTS = 996;
	public static final Integer USER_NOT_EXISTS = 995;
	public static final Integer USER_CREATED = 2001;
	public static final Integer INVALID_REFRESH_TOKEN = 001;
	
	public static final Integer OTP_SENT_SUCCESSFUL = 2020;

	public static final Integer OTP_EXPIRED = 994;
	public static final Integer OTP_INVALID = 993;
	public static final Integer WITHDRAWAL_CANNOT_CANCEL = 800;
	public static final Integer INVALID_AMOUNT = 801;
	public static final Integer WITHDRAWAL_LOCKED = 802;
	public static final Integer DUPLICATE_REQUEST = 803;
}

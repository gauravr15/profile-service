package com.odin.profileservice.enums;

public enum OTPType {
	
	REGISTRATION(2020l), 
	SIGNIN(2021l);
	
	long value;

	OTPType(long l) {
		this.value = l;
	}
	
	public long getValue() {
		return value;
	}

}

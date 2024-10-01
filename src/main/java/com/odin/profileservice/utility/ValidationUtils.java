package com.odin.profileservice.utility;

import org.springframework.util.ObjectUtils;

public class ValidationUtils {
	
	public static boolean isValid(Object obj) {
		if(ObjectUtils.isEmpty(obj)){
			return false;
		}
		return true;
	}

}

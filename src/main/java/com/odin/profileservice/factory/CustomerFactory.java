package com.odin.profileservice.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.enums.CustomerType;
import com.odin.profileservice.service.LoginService;
import com.odin.profileservice.service.impl.LoginServiceImpl;



@Component
public class CustomerFactory {
	
	@Autowired
	LoginServiceImpl customer;
	
	public LoginService getInstance(ProfileDTO profile) {
		switch(profile.getCustomerType()) {
		case CUSTOMER:
			return customer;
		default:
			return null;
		}
	}
	
	public LoginService getInstance(CustomerType str) {
		switch(str) {
		case CUSTOMER:
			return customer;
		default:
			return null;
		}
	}

}

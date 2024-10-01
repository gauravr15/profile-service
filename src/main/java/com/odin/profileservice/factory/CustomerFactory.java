package com.odin.profileservice.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.service.FetchService;
import com.odin.profileservice.service.impl.FetchCustomerServiceImpl;



@Component
public class CustomerFactory {
	
	@Autowired
	FetchCustomerServiceImpl customer;
	
	public FetchService getInstance(ProfileDTO profile) {
		switch(profile.getCustomerType()) {
		case CUSTOMER:
			return customer;
		default:
			return null;
		}
	}

}

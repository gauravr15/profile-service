package com.odin.profileservice.entity;


import com.odin.profileservice.enums.CustomerType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerProfile {
	
	private String mobile;
	private String email;
	private String password;
	private String firstName;
	private String lastName;
	private String fathersName;
	private CustomerType customerType;
	private String customerSubType;
	private String idType;
	private String idNum;
	private String address;
	private String bankDetails;
	private String customerid;
	private boolean isTransactionEnabled;
}

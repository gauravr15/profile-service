package com.odin.profileservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "RESPONSE_MESSAGES")
@Getter
@Setter
public class ResponseMessages {
	
	@Id
	Integer id;
	
	@Column(name = "MESSAGE_EN")
	String messageEn;
	
	@Column(name = "MESSAGE_CH")
	String messageCh;
	
	@Column(name = "MESSAGE_HI")
	String messageHi;
	
	@Column(name = "MESSAGE_SP")
	String messageSp;
	
	@Column(name = "MESSAGE_FR")
	String messageFr;
	
	@Column(name = "MESSAGE_AR")
	String messageAr;
	
	@Column(name = "MESSAGE_BG")
	String messageBg;
	
	@Column(name = "MESSAGE_PG")
	String messagePg;
	
	@Column(name = "MESSAGE_RS")
	String messageRs;
	
	@Column(name = "MESSAGE_UR")
	String messageUr;
}

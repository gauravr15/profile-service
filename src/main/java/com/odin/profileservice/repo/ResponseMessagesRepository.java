package com.odin.profileservice.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.ResponseMessages;


@Repository
public interface ResponseMessagesRepository extends JpaRepository<ResponseMessages, Integer> {

}

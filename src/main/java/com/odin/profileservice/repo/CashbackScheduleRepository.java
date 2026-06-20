package com.odin.profileservice.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.CashbackSchedule;

@Repository
public interface CashbackScheduleRepository extends JpaRepository<CashbackSchedule, Long> {

	List<CashbackSchedule> findByStatusAndEligibleAtLessThanEqual(String status, LocalDateTime dateTime);

	List<CashbackSchedule> findByStatusOrderByEligibleAtAsc(String status);

	@Query(value = "SELECT cs.* " + "FROM cashback_schedule cs " + "INNER JOIN customer_investment ci "
			+ "ON cs.investment_id = ci.id " + "WHERE ci.customer_id = :customerId "
			+ "AND cs.status = :status", nativeQuery = true)
	List<CashbackSchedule> findByCustomerAndStatus(@Param("customerId") Integer customerId,
			@Param("status") String status);

	CashbackSchedule findByWithdrawalRequestId(Long requestId);
	
	List<CashbackSchedule> findByInvestmentIdAndStatus(Long investmentId, String status);
}
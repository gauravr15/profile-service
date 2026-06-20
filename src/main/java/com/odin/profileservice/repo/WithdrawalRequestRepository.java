package com.odin.profileservice.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.odin.profileservice.entity.WithdrawalRequest;
import com.odin.profileservice.enums.WithdrawalStatus;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

	/**
	 * Dashboard count
	 */
	@Query("SELECT COUNT(w) FROM WithdrawalRequest w WHERE w.customerId = :customerId AND w.status IN ('PENDING','APPROVED_UNPAID')")
	Long countPendingWithdrawals(@Param("customerId") Integer customerId);

	/**
	 * Customer withdrawal history
	 */
	Page<WithdrawalRequest> findByCustomerIdOrderByCreatedAtDesc(Integer customerId, Pageable pageable);

	/**
	 * Investment withdrawal history
	 */
	List<WithdrawalRequest> findByInvestmentIdOrderByCreatedAtDesc(Long investmentId);

	/**
	 * Find request owned by customer
	 */
	Optional<WithdrawalRequest> findByIdAndCustomerId(Long id, Integer customerId);

	/**
	 * Requests waiting for report generation
	 */
	List<WithdrawalRequest> findByReportGeneratedFalseAndStatus(String status);

	/**
	 * Requests already included in report
	 */
	List<WithdrawalRequest> findByReportGeneratedTrueAndStatus(String status);

	/**
	 * Total amount participating in withdrawal cap
	 *
	 * PENDING + APPROVED_UNPAID
	 */
	@Query("SELECT COALESCE(SUM(w.requestedAmount),0) FROM WithdrawalRequest w WHERE w.status IN ('PENDING','APPROVED_UNPAID')")
	BigDecimal getCurrentOutstandingWithdrawalAmount();

	/**
	 * Customer outstanding withdrawals
	 */
	@Query("SELECT COALESCE(SUM(w.requestedAmount),0) FROM WithdrawalRequest w WHERE w.customerId = :customerId AND w.status IN ('PENDING','APPROVED_UNPAID')")
	BigDecimal getOutstandingWithdrawalAmountByCustomer(@Param("customerId") Integer customerId);

	/**
	 * Used to check if cashback was already awarded today
	 */
	@Query("SELECT COUNT(w) FROM WithdrawalRequest w WHERE w.investmentId = :investmentId AND w.cashbackToBeCredited > 0 AND DATE(w.createdAt) = :businessDate")
	Long countCashbackAwardedToday(@Param("investmentId") Long investmentId,
			@Param("businessDate") LocalDate businessDate);

	/**
	 * Withdrawal requests awaiting manual payout
	 */
	List<WithdrawalRequest> findByStatusOrderByRequestTimeAsc(String status);

	/**
	 * Requests eligible for report generation
	 */
	@Query("SELECT w FROM WithdrawalRequest w WHERE w.status = 'PENDING' AND w.requestTime <= :cutoffTime AND w.reportGenerated = false")
	List<WithdrawalRequest> findEligibleForReportGeneration(@Param("cutoffTime") LocalDateTime cutoffTime);

	/**
	 * Transaction screen filters
	 */
	@Query("SELECT w FROM WithdrawalRequest w WHERE w.customerId = :customerId AND w.createdAt BETWEEN :fromDate AND :toDate ORDER BY w.createdAt DESC")
	Page<WithdrawalRequest> findCustomerWithdrawals(@Param("customerId") Integer customerId,
			@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate, Pageable pageable);

	Long countByCustomerIdAndStatusIn(Integer customerId, List<WithdrawalStatus> statusList);

	List<WithdrawalRequest> findByCustomerIdOrderByRequestTimeDesc(Integer customerId);

	boolean existsByCustomerIdAndInvestmentIdAndStatus(Integer customerId, Long investmentId, WithdrawalStatus status);

}
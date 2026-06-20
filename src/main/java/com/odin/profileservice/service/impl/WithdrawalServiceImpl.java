package com.odin.profileservice.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.WithdrawalEligibilityResponse;
import com.odin.profileservice.dto.WithdrawalListResponse;
import com.odin.profileservice.dto.WithdrawalLockResponse;
import com.odin.profileservice.dto.WithdrawalPreviewRequest;
import com.odin.profileservice.dto.WithdrawalPreviewResponse;
import com.odin.profileservice.dto.WithdrawalRequestDto;
import com.odin.profileservice.dto.WithdrawalRequestResponse;
import com.odin.profileservice.entity.CashbackRule;
import com.odin.profileservice.entity.CashbackSchedule;
import com.odin.profileservice.entity.CustomerInvestment;
import com.odin.profileservice.entity.WithdrawalLock;
import com.odin.profileservice.entity.WithdrawalRequest;
import com.odin.profileservice.enums.WithdrawalStatus;
import com.odin.profileservice.repo.CashbackRuleRepository;
import com.odin.profileservice.repo.CashbackScheduleRepository;
import com.odin.profileservice.repo.CustomerInvestmentRepository;
import com.odin.profileservice.repo.WithdrawalLockRepository;
import com.odin.profileservice.repo.WithdrawalRequestRepository;
import com.odin.profileservice.service.WithdrawalService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class WithdrawalServiceImpl implements WithdrawalService {

	@Autowired
	private CustomerInvestmentRepository investmentRepo;

	@Autowired
	private WithdrawalRequestRepository withdrawalRepo;

	@Autowired
	private CashbackRuleRepository cashbackRuleRepo;

	@Autowired
	private CashbackScheduleRepository cashbackScheduleRepo;

	@Autowired
	private WithdrawalLockRepository lockRepo;

	@Autowired
	private ResponseObject response;

	@Override
	public ResponseDTO previewWithdrawal(Integer customerId, WithdrawalPreviewRequest request) {

		CustomerInvestment investment = investmentRepo.findByIdAndCustomerId(request.getInvestmentId(), customerId)
				.orElse(null);

		if (investment == null) {
			return response.buildResponse(ResponseCodes.NO_DATA_FOUND);
		}

		if (request.getPrincipalWithdrawal().compareTo(BigDecimal.ZERO) < 0
				|| request.getProfitWithdrawal().compareTo(BigDecimal.ZERO) < 0
				|| request.getCashbackWithdrawal().compareTo(BigDecimal.ZERO) < 0) {
			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (investment.getPrincipalBalance().compareTo(BigDecimal.ZERO) <= 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (request.getPrincipalWithdrawal().compareTo(investment.getPrincipalBalance()) > 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (request.getProfitWithdrawal().compareTo(investment.getProfitBalance()) > 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (request.getPrincipalWithdrawal().add(request.getProfitWithdrawal()).add(request.getCashbackWithdrawal())
				.compareTo(BigDecimal.ZERO) <= 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		Optional<WithdrawalLock> lock = lockRepo.findFirstByStatusOrderByLockedAtDesc("ACTIVE");

		if (lock.isPresent()) {

			return response.buildResponse(ResponseCodes.WITHDRAWAL_LOCKED);
		}

		BigDecimal withdrawalAmount = request.getPrincipalWithdrawal().add(request.getProfitWithdrawal())
				.add(request.getCashbackWithdrawal());

		BigDecimal remainingPrincipal = investment.getPrincipalBalance().subtract(request.getPrincipalWithdrawal());

		if (investment.getPrincipalBalance().compareTo(BigDecimal.ZERO) <= 0) {
			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		BigDecimal withdrawalPercent = request.getPrincipalWithdrawal().multiply(BigDecimal.valueOf(100))
				.divide(investment.getPrincipalBalance(), 2, RoundingMode.HALF_UP);

		CashbackRule rule = cashbackRuleRepo
				.findFirstByWithdrawalPercentFromLessThanEqualAndWithdrawalPercentToGreaterThanEqualAndActive(
						withdrawalPercent, withdrawalPercent, true);

		BigDecimal cashbackPercent = rule != null ? rule.getCashbackPercent() : BigDecimal.ZERO;

		BigDecimal cashbackToBeCredited = remainingPrincipal.multiply(cashbackPercent).divide(BigDecimal.valueOf(100),
				2, RoundingMode.HALF_UP);

		WithdrawalPreviewResponse dto = WithdrawalPreviewResponse.builder().withdrawAmount(withdrawalAmount)
				.remainingPrincipal(remainingPrincipal).cashbackPercentage(cashbackPercent)
				.cashbackToBeCredited(cashbackToBeCredited).cashbackEligibleAt(LocalDateTime.now().plusDays(30))
				.build();

		return response.buildResponse(ResponseCodes.SUCCESS_CODE, dto);
	}

	@Transactional
	@Override
	public ResponseDTO createWithdrawalRequest(Integer customerId, WithdrawalRequestDto request) {

		CustomerInvestment investment = investmentRepo.findByIdAndCustomerId(request.getInvestmentId(), customerId)
				.orElseThrow(RuntimeException::new);

		boolean exists = withdrawalRepo.existsByCustomerIdAndInvestmentIdAndStatus(customerId,
				request.getInvestmentId(), WithdrawalStatus.PENDING);

		if (exists) {
			return response.buildResponse(ResponseCodes.DUPLICATE_REQUEST);
		}

		if (request.getPrincipalAmount().compareTo(BigDecimal.ZERO) < 0
				|| request.getProfitAmount().compareTo(BigDecimal.ZERO) < 0
				|| request.getCashbackAmount().compareTo(BigDecimal.ZERO) < 0) {
			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		Optional<WithdrawalLock> lock = lockRepo.findFirstByStatusOrderByLockedAtDesc("ACTIVE");

		if (lock.isPresent()) {

			return response.buildResponse(ResponseCodes.WITHDRAWAL_LOCKED);
		}

		if (request.getPrincipalAmount().compareTo(investment.getPrincipalBalance()) > 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (request.getProfitAmount().compareTo(investment.getProfitBalance()) > 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		if (request.getCashbackAmount().compareTo(investment.getCashbackBalance()) > 0) {

			return response.buildResponse(ResponseCodes.INVALID_AMOUNT);
		}

		BigDecimal remainingPrincipal = investment.getPrincipalBalance().subtract(request.getPrincipalAmount());

		BigDecimal withdrawalPercent = request.getPrincipalAmount().multiply(BigDecimal.valueOf(100))
				.divide(investment.getPrincipalBalance(), 2, RoundingMode.HALF_UP);

		CashbackRule rule = cashbackRuleRepo
				.findFirstByWithdrawalPercentFromLessThanEqualAndWithdrawalPercentToGreaterThanEqualAndActive(
						withdrawalPercent, withdrawalPercent, true);

		BigDecimal cashbackPercent = rule != null ? rule.getCashbackPercent() : BigDecimal.ZERO;

		BigDecimal cashbackToBeCredited = remainingPrincipal.multiply(cashbackPercent).divide(BigDecimal.valueOf(100),
				2, RoundingMode.HALF_UP);

		LocalDateTime cashbackEligibleDate = LocalDateTime.now();//.plusDays(30);

		BigDecimal total = request.getPrincipalAmount().add(request.getProfitAmount()).add(request.getCashbackAmount());

		WithdrawalRequest entity = WithdrawalRequest.builder().customerId(customerId).investmentId(investment.getId())
				.requestedAmount(total).principalAmount(request.getPrincipalAmount())
				.profitAmount(request.getProfitAmount()).cashbackAmount(request.getCashbackAmount())
				.cashbackToBeCredited(cashbackToBeCredited)
				.requestTime(LocalDateTime.now()).status(WithdrawalStatus.PENDING).build();

		withdrawalRepo.save(entity);

		CashbackSchedule cashbackSchedule = CashbackSchedule.builder().withdrawalRequestId(entity.getId())
				.investmentId(investment.getId()).cashbackAmount(cashbackToBeCredited).eligibleAt(cashbackEligibleDate)
				.status("PENDING").build();

		cashbackScheduleRepo.save(cashbackSchedule);

		WithdrawalRequestResponse responseDto = WithdrawalRequestResponse.builder().withdrawalRequestId(entity.getId())
				.requestedAmount(total).status(entity.getStatus().name()).build();

		return response.buildResponse(ResponseCodes.SUCCESS_CODE, responseDto);
	}

	@Override
	public ResponseDTO getRequests(Integer customerId) {

		List<WithdrawalListResponse> result = withdrawalRepo.findByCustomerIdOrderByRequestTimeDesc(customerId).stream()
				.map(x -> WithdrawalListResponse.builder().requestId(x.getId()).amount(x.getRequestedAmount())
						.status(x.getStatus().name()).requestTime(x.getRequestTime()).build())
				.collect(Collectors.toList());

		return response.buildResponse(ResponseCodes.SUCCESS_CODE, result);
	}

	@Override
	@Transactional
	public ResponseDTO cancelWithdrawal(Integer customerId, Long requestId) {

		WithdrawalRequest request = withdrawalRepo.findByIdAndCustomerId(requestId, customerId).orElse(null);

		if (request == null) {
			return response.buildResponse(ResponseCodes.NO_DATA_FOUND);
		}
		
		if (request.getStatus() == WithdrawalStatus.PROCESSING) {
		    return response.buildResponse(ResponseCodes.WITHDRAWAL_CANNOT_CANCEL);
		}

		if (Boolean.TRUE.equals(request.getReportGenerated())) {

			return response.buildResponse(ResponseCodes.WITHDRAWAL_CANNOT_CANCEL);
		}

		request.setStatus(WithdrawalStatus.CANCELLED);

		CashbackSchedule schedule = cashbackScheduleRepo.findByWithdrawalRequestId(requestId);

		if (schedule != null) {

			schedule.setStatus("CANCELLED");

			cashbackScheduleRepo.save(schedule);
		}

		withdrawalRepo.save(request);

		return response.buildResponse(ResponseCodes.SUCCESS_CODE);
	}

	@Override
	public ResponseDTO getLockStatus() {

		Optional<WithdrawalLock> lock = lockRepo.findFirstByStatusOrderByLockedAtDesc("ACTIVE");

		WithdrawalLockResponse dto = WithdrawalLockResponse.builder().locked(lock.isPresent())
				.unlockAt(lock.map(WithdrawalLock::getUnlockAt).orElse(null))
				.reason(lock.map(WithdrawalLock::getReason).orElse(null)).build();

		return response.buildResponse(ResponseCodes.SUCCESS_CODE, dto);
	}

	@Override
	public ResponseDTO getEligibility(Integer customerId, Long investmentId) {

		CustomerInvestment investment = investmentRepo.findByIdAndCustomerId(investmentId, customerId).orElse(null);

		if (investment == null) {
			return response.buildResponse(ResponseCodes.NO_DATA_FOUND);
		}

		Optional<WithdrawalLock> lock = lockRepo.findFirstByStatusOrderByLockedAtDesc("ACTIVE");

		boolean isLocked = lock.isPresent();

		BigDecimal principal = investment.getPrincipalBalance();
		BigDecimal profit = investment.getProfitBalance();
		BigDecimal cashback = investment.getCashbackBalance();

		BigDecimal lockedCashback = cashbackScheduleRepo.findByInvestmentIdAndStatus(investmentId, "PENDING").stream()
				.map(CashbackSchedule::getCashbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		// basic eligibility checks
		boolean hasBalance = principal.compareTo(BigDecimal.ZERO) > 0 || profit.compareTo(BigDecimal.ZERO) > 0
				|| cashback.compareTo(BigDecimal.ZERO) > 0;

		boolean canWithdraw = !isLocked && hasBalance;

		WithdrawalEligibilityResponse dto = WithdrawalEligibilityResponse.builder().availablePrincipal(principal)
				.availableProfit(profit).eligibleCashback(cashback).lockedCashback(lockedCashback)
				.canWithdraw(canWithdraw).withdrawalLock(isLocked).nextCashbackEligibility(LocalDate.now().plusDays(30))
				.build();

		return response.buildResponse(ResponseCodes.SUCCESS_CODE, dto);
	}

}
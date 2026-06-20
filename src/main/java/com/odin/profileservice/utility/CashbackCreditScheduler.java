package com.odin.profileservice.utility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.odin.profileservice.entity.CashbackSchedule;
import com.odin.profileservice.entity.CustomerInvestment;
import com.odin.profileservice.entity.InvestmentLedger;
import com.odin.profileservice.entity.WithdrawalRequest;
import com.odin.profileservice.enums.WithdrawalStatus;
import com.odin.profileservice.repo.CashbackScheduleRepository;
import com.odin.profileservice.repo.CustomerInvestmentRepository;
import com.odin.profileservice.repo.InvestmentLedgerRepository;
import com.odin.profileservice.repo.WithdrawalRequestRepository;

@Component
public class CashbackCreditScheduler {

    @Autowired
    private CashbackScheduleRepository scheduleRepo;

    @Autowired
    private CustomerInvestmentRepository investmentRepo;

    @Autowired
    private InvestmentLedgerRepository ledgerRepo;
    
    @Autowired
	private WithdrawalRequestRepository withdrawalRepo;

    @Scheduled(cron = "0 1/10 * * * *")
    @Transactional
    public void creditCashback() {

        List<CashbackSchedule> schedules =
                scheduleRepo
                        .findByStatusAndEligibleAtLessThanEqual(
                                "PENDING",
                                LocalDateTime.now());

        for (CashbackSchedule schedule : schedules) {

            CustomerInvestment investment =
                    investmentRepo.findById(
                            schedule.getInvestmentId())
                            .orElse(null);

            if (investment == null) {
                continue;
            }

            investment.setCashbackBalance(
                    investment.getCashbackBalance()
                            .add(
                                    schedule.getCashbackAmount()));

            investmentRepo.save(investment);

            schedule.setCreditedAt(
                    LocalDateTime.now());

            schedule.setStatus("PROCESSING");
            
            Optional<WithdrawalRequest> withdrawalreq = withdrawalRepo.findById(schedule.getWithdrawalRequestId());
            if(withdrawalreq.isPresent()) {
            	WithdrawalRequest req = withdrawalreq.get();
            	req.setStatus(WithdrawalStatus.PROCESSING);
            	withdrawalRepo.save(req);
            	scheduleRepo.save(schedule);
            }
            

            createLedger(
                    investment,
                    schedule);
        }
    }

    private void createLedger(
            CustomerInvestment investment,
            CashbackSchedule schedule) {

        InvestmentLedger ledger =
                InvestmentLedger.builder()
                        .customerId(
                                investment.getCustomerId())
                        .investmentId(
                                investment.getId())
                        .txnType(
                                "CASHBACK_CREDIT")
                        .amount(
                                schedule.getCashbackAmount())
                        .referenceId(
                                schedule.getId())
                        .balanceAfter(
                                investment.getCashbackBalance())
                        .remarks(
                                "Cashback credited")
                        .createdAt(LocalDateTime.now())
                        .build();

        ledgerRepo.save(ledger);
    }
}
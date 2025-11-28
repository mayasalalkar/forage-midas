package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final org.springframework.web.client.RestTemplate restTemplate;

    public TransactionService(UserRepository userRepository, TransactionRecordRepository transactionRecordRepository,
            org.springframework.web.client.RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void processTransaction(Transaction transaction) {
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        if (sender == null || recipient == null) {
            logger.warn("Transaction failed: Invalid sender or recipient. Sender ID: {}, Recipient ID: {}",
                    transaction.getSenderId(), transaction.getRecipientId());
            return;
        }

        if (sender.getBalance() < transaction.getAmount()) {
            logger.warn("Transaction failed: Insufficient funds. Sender ID: {}, Balance: {}, Amount: {}",
                    sender.getId(), sender.getBalance(), transaction.getAmount());
            return;
        }

        float incentiveAmount = 0;
        try {
            com.jpmc.midascore.foundation.Incentive incentive = restTemplate.postForObject(
                    "http://localhost:8080/incentive", transaction, com.jpmc.midascore.foundation.Incentive.class);
            if (incentive != null) {
                incentiveAmount = incentive.getAmount();
            }
        } catch (Exception e) {
            logger.error("Error calling incentive API", e);
        }

        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        userRepository.save(sender);
        userRepository.save(recipient);

        TransactionRecord record = new TransactionRecord(sender, recipient, transaction.getAmount());
        record.setIncentiveAmount(incentiveAmount);
        transactionRecordRepository.save(record);

        logger.info("Transaction processed successfully: {}", record);
    }
}

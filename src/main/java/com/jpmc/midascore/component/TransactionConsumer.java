package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class TransactionConsumer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @KafkaListener(topics = "test-topic", groupId = "midas-core")
    public void listen(Transaction transaction) {
        long senderId = transaction.getSenderId();
        long recipientId = transaction.getRecipientId();
        float amount = transaction.getAmount();

        Optional<UserRecord> senderOpt = userRepository.findById(senderId);
        Optional<UserRecord> recipientOpt = userRepository.findById(recipientId);

        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            System.out.println("Invalid sender or recipient. Discarded.");
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        if (sender.getBalance() >= amount) {
            // Send to Incentive API
            Incentive incentive = restTemplate.postForObject(
                "http://localhost:8080/incentive",
                transaction,
                Incentive.class
            );

            float incentiveAmount = incentive != null ? incentive.getAmount() : 0.0f;

            sender.setBalance(sender.getBalance() - amount);
            recipient.setBalance(recipient.getBalance() + amount + incentiveAmount);

            userRepository.save(sender);
            userRepository.save(recipient);

            TransactionRecord record = new TransactionRecord();
            record.setSenderId(senderId);
            record.setRecipientId(recipientId);
            record.setAmount(amount);
            transactionRecordRepository.save(record);

            System.out.println("Transaction processed with incentive: " + incentiveAmount);
        } else {
            System.out.println("Sender has insufficient balance. Discarded.");
        }
    }
}

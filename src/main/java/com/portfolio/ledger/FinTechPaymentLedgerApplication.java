package com.portfolio.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinTechPaymentLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinTechPaymentLedgerApplication.class, args);
	}

}

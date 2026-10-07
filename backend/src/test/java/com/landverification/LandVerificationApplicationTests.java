package com.landverification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "web3j.client-address=http://127.0.0.1:8545",
    "blockchain.contract-address=0x0000000000000000000000000000000000000000",
    "blockchain.private-key=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
})
class LandVerificationApplicationTests {

    @Test
    void contextLoads() {
        // Confirms the Spring context starts without errors
    }
}

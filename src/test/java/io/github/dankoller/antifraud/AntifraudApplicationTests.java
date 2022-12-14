package io.github.dankoller.antifraud;

import io.github.dankoller.antifraud.controller.AuthorizationController;
import io.github.dankoller.antifraud.controller.TransactionController;
import io.github.dankoller.antifraud.controller.ValidationController;
import io.github.dankoller.antifraud.entity.Card;
import io.github.dankoller.antifraud.entity.user.User;
import io.github.dankoller.antifraud.persistence.CardRepository;
import io.github.dankoller.antifraud.persistence.TransactionRepository;
import io.github.dankoller.antifraud.persistence.UserRepository;
import io.github.dankoller.antifraud.service.UserService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unused")
class AntifraudApplicationTests {

    // Users
    private final User testAdministrator = new User("Test Administrator",
            "testadmin",
            "password",
            "ROLE_ADMINISTRATOR",
            true);
    private final String testMerchantUsername = "testmerchant";
    private final String testSupportName = "Test Support";
    private final String testSupportUsername = "testsupport";
    private final String testPassword = "password";

    // Card numbers
    private final String cardNumberValid = "4000008449430003";
    private final String cardNumberInvalid = "1234567891011121";
    private final String stolenCardNumberValid = "3151853279026036";
    private final String stolenCardNumberValidAsJson = "{" + "\"number\":\"" + stolenCardNumberValid + "\"}";

    // IP addresses
    private final String ipValid = "127.0.0.1";
    private final String suspiciousIpValid = "127.127.127.127";
    private final String suspiciousIpValidAsJson = "{" + "\"ip\":\"" + suspiciousIpValid + "\"}";

    // Regions
    private final String regionValid = "ECA";

    // Dates
    private final String dateValid = "2022-10-13T14:34:41";

    // Transactions
    private final String amountValid = "800";
    private final String transactionValidAsJson = "{" +
            "\"amount\":\"" + amountValid +
            "\",\"ip\":\"" + ipValid +
            "\",\"number\":\"" + cardNumberValid +
            "\",\"region\":\"" + regionValid +
            "\",\"date\":\"" + dateValid +
            "\"}";

    // Feedback
    private final String feedbackAsJsonInvalid = "{" +
            "\"transactionId\":\"" + "0" +
            "\",\"feedback\":\"" + "ALLOWED" +
            "\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthorizationController authorizationController;

    @Autowired
    private TransactionController transactionController;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ValidationController validationController;

    @Autowired
    private CardRepository cardRepository;

    // Test if the controllers are initialized
    @Test
    @Order(1)
    void contextLoads() {
        assertThat(authorizationController).isNotNull();
        assertThat(transactionController).isNotNull();
        assertThat(validationController).isNotNull();
    }

    // Save an admin user to the database
    @Test
    @Order(2)
    void saveAdminUser() {
        // Encode the password
        testAdministrator.setPassword(new BCryptPasswordEncoder().encode(testAdministrator.getPassword()));

        userRepository.save(testAdministrator);
        assertThat(userRepository.findByUsername(testAdministrator.getUsername())).isNotNull();
    }

    // Test if the (admin) user can log in
    @Test
    @Order(3)
    void loginAdminUser() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + testAdministrator.getUsername() +
                                "\",\"password\":\"" + testAdministrator.getPassword() + "\"}"))
                .andExpect(status().isOk())
                // Response should contain name, username and role
                .andExpect(content().string(containsString(testAdministrator.getName())))
                .andExpect(content().string(containsString(testAdministrator.getUsername())))
                .andExpect(content().string(containsString(testAdministrator.getRoleWithoutPrefix())));
    }

    // Test if a non-existing user can't log in
    @Test
    @Order(4)
    void loginNonExistingUser() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexisting\",\"password\":\"password\"}"))
                .andExpect(status().isNotFound());
    }

    // Test if a merchant user can be created
    @Test
    @Order(5)
    void testMerchantCreation() throws Exception {
        String testMerchantName = "Test Merchant";
        String testMerchantAsJson = "{" +
                "\"name\":\"" + testMerchantName +
                "\",\"username\":\"" + testMerchantUsername +
                "\",\"password\":\"" + testPassword +
                "\"}";
        mvc
                .perform(post("/api/auth/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testMerchantAsJson))
                // Check the response status code
                .andExpect(status().isCreated())
                // Check that the response contains id, name, username, and role
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("name")))
                // Name should be the same as the one we sent
                .andExpect(content().string(containsString(testMerchantName)))
                .andExpect(content().string(containsString("username")))
                // Username should be the same as the one we sent
                .andExpect(content().string(containsString(testMerchantUsername)))
                .andExpect(content().string(containsString("role")))
                // Role should be MERCHANT by default
                .andExpect(content().string(containsString("MERCHANT")));
    }

    // Test if a support user can be created
    @Test
    @Order(6)
    void testSupportCreation() throws Exception {
        String testSupportAsJson = "{" +
                "\"name\":\"" + testSupportName +
                "\",\"username\":\"" + testSupportUsername +
                "\",\"password\":\"" + testPassword +
                "\"}";
        mvc
                .perform(post("/api/auth/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testSupportAsJson))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("name")))
                .andExpect(content().string(containsString(testSupportName)))
                .andExpect(content().string(containsString("username")))
                .andExpect(content().string(containsString(testSupportUsername)))
                .andExpect(content().string(containsString("role")))
                .andExpect(content().string(containsString("MERCHANT"))); // MERCHANT by default
    }

    // Test if users can be unlocked
    @Test
    @Order(7)
    @WithMockUser(username = "testadmin", roles = {"ADMINISTRATOR"})
    void testUnlockUser() throws Exception {
        String unlockMerchantAsJson = "{\"username\":\"" + testMerchantUsername +
                "\",\"operation\":\"" + "UNLOCK" +
                "\"}";

        String unlockSupportAsJson = "{\"username\":\"" + testSupportUsername +
                "\",\"operation\":\"" + "UNLOCK" +
                "\"}";

        mvc
                .perform(put("/api/auth/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unlockMerchantAsJson))
                .andExpect(status().isOk())
                // Response should contain status field
                .andExpect(content().string(containsString("status")))
                // Status should be 'User <username> unlocked!'
                .andExpect(content().string(containsString("User testmerchant unlocked!")));

        mvc
                .perform(put("/api/auth/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unlockSupportAsJson))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("User testsupport unlocked!")));
    }

    // Check if a role can be changed
    @Test
    @Order(8)
    @WithMockUser(username = "testadmin", roles = {"ADMINISTRATOR"})
    void testChangeRole() throws Exception {
        String changeRoleAsJson = "{\"username\":\"" + testSupportUsername +
                "\",\"role\":\"" + "SUPPORT" +
                "\"}";

        mvc
                .perform(put("/api/auth/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRoleAsJson))
                .andExpect(status().isOk())
                // Response should contain id, name, username, and role
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("name")))
                .andExpect(content().string(containsString(testSupportName)))
                .andExpect(content().string(containsString("username")))
                .andExpect(content().string(containsString(testSupportUsername)))
                .andExpect(content().string(containsString("role")))
                // Role should be SUPPORT
                .andExpect(content().string(containsString("SUPPORT")));
    }

    // Test if the support can get a list of all users
    @Test
    @Order(9)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testGetAllUsers() throws Exception {
        mvc
                .perform(get("/api/auth/list"))
                .andExpect(status().isOk())
                // The response should contain the testadmin, testmerchant, and testsupport users as list
                .andExpect(content().string(containsString("testadmin"))) // Admin is created differently
                .andExpect(content().string(containsString(testMerchantUsername)))
                .andExpect(content().string(containsString(testSupportUsername)));
    }

    // Test if the admin can get a list of all users
    @Test
    @Order(10)
    @WithMockUser(username = "testadmin", roles = {"ADMINISTRATOR"})
    void testGetAllUsersAdmin() throws Exception {
        mvc
                .perform(get("/api/auth/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("testadmin")))
                .andExpect(content().string(containsString(testMerchantUsername)))
                .andExpect(content().string(containsString(testSupportUsername)));
    }

    // Test if the merchant can't get a list of all users
    @Test
    @Order(11)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testGetAllUsersMerchant() throws Exception {
        mvc
                .perform(get("/api/auth/list"))
                .andExpect(status().isForbidden());
    }

    // Test if the merchant can post a new transaction
    @Test
    @Order(12)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransaction() throws Exception {
        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionValidAsJson))
                .andExpect(status().isOk())
                // The response should contain the result and info fields
                .andExpect(content().string(containsString("result")))
                // Should be MANUAL_PROCESSING
                .andExpect(content().string(containsString("MANUAL_PROCESSING")))
                .andExpect(content().string(containsString("info")))
                // Should be 'amount'
                .andExpect(content().string(containsString("amount")));
    }

    // Test if the merchant can't post a new transaction with an invalid date
    @Test
    @Order(13)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransactionInvalidDate() throws Exception {
        String transactionBadDateAsJson = transactionValidAsJson.replace(dateValid, "2022-10-13");

        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBadDateAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't post a new transaction with an invalid amount
    @Test
    @Order(14)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransactionInvalidAmount() throws Exception {
        String transactionBadAmountAsJson = transactionValidAsJson.replace(amountValid, "");

        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBadAmountAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't post a new transaction with an invalid ip
    @Test
    @Order(15)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransactionInvalidIp() throws Exception {
        String transactionBadIpAsJson = transactionValidAsJson.replace(ipValid, "");

        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBadIpAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't post a new transaction with an invalid number
    @Test
    @Order(16)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransactionInvalidNumber() throws Exception {
        String transactionBadNumberAsJson = transactionValidAsJson.replace(cardNumberValid, cardNumberInvalid);

        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBadNumberAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't post a new transaction with an invalid region
    @Test
    @Order(17)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostTransactionInvalidRegion() throws Exception {
        String regionInvalid = "ABC";
        String transactionbadRegionAsJson = transactionValidAsJson.replace(regionValid, regionInvalid);

        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionbadRegionAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the support can't post a new transaction
    @Test
    @Order(18)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPostTransactionSupport() throws Exception {
        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the admin can't post a new transaction
    @Test
    @Order(19)
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testPostTransactionAdmin() throws Exception {
        mvc
                .perform(post("/api/antifraud/transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the support can post a new suspicious ip
    @Test
    @Order(20)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPostSuspiciousIpSupport() throws Exception {
        mvc
                .perform(post("/api/antifraud/suspicious-ip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suspiciousIpValidAsJson))
                .andExpect(status().isOk())
                // Response should contain an id and the ip field
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("ip")))
                // Response should contain the ip we sent
                .andExpect(content().string(containsString(suspiciousIpValid)));
    }

    // Test if the support can't post a new suspicious ip with an invalid ip
    @Test
    @Order(21)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPostSuspiciousIpInvalidIp() throws Exception {
        String suspiciousIpBadIpAsJson = suspiciousIpValidAsJson.replace(suspiciousIpValid, "");

        mvc
                .perform(post("/api/antifraud/suspicious-ip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suspiciousIpBadIpAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the support can get a list of suspicious ips
    @Test
    @Order(22)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testGetSuspiciousIpsSupport() throws Exception {
        mvc
                .perform(get("/api/antifraud/suspicious-ip"))
                .andExpect(status().isOk())
                // Response should contain an id and the ip field
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("ip")))
                // Response should contain the ip we sent earlier
                .andExpect(content().string(containsString(suspiciousIpValid)));
    }

    // Test if the support can delete a suspicious ip
    @Test
    @Order(23)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteSuspiciousIpSupport() throws Exception {
        mvc
                .perform(delete("/api/antifraud/suspicious-ip/" + suspiciousIpValid))
                .andExpect(status().isOk())
                // Response should contain a status field
                .andExpect(content().string(containsString("status")))
                // Response should contain the status "IP <ip> successfully removed!"
                .andExpect(content().string(containsString("IP " + suspiciousIpValid + " successfully removed!")));
    }

    // Test if the support can't delete a suspicious ip with an invalid ip
    @Test
    @Order(24)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteSuspiciousIpInvalidIp() throws Exception {
        String emptyIp = "";
        String badIp = "123";

        mvc
                .perform(delete("/api/antifraud/suspicious-ip/" + emptyIp))
                .andExpect(status().isMethodNotAllowed());

        mvc
                .perform(delete("/api/antifraud/suspicious-ip/" + badIp))
                .andExpect(status().isBadRequest());
    }

    // Test if the support can't delete a suspicious ip with an ip that doesn't exist
    @Test
    @Order(25)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteSuspiciousIpIpDoesntExist() throws Exception {
        String badIpNonExisting = "10.11.12.13";

        mvc
                .perform(delete("/api/antifraud/suspicious-ip/" + badIpNonExisting))
                .andExpect(status().isNotFound());
    }

    // Test if the merchant can't post a new suspicious ip
    @Test
    @Order(26)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostSuspiciousIpMerchant() throws Exception {
        mvc
                .perform(post("/api/antifraud/suspicious-ip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suspiciousIpValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the admin can't post a new suspicious ip
    @Test
    @Order(27)
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testPostSuspiciousIpAdmin() throws Exception {
        mvc
                .perform(post("/api/antifraud/suspicious-ip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suspiciousIpValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the support can post a new stolen card number
    @Test
    @Order(28)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPostStolenCardNumberSupport() throws Exception {
        mvc
                .perform(post("/api/antifraud/stolencard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stolenCardNumberValidAsJson))
                .andExpect(status().isOk())
                // Response should contain an id and the number field
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("number")))
                // Response should contain the number we sent
                .andExpect(content().string(containsString(stolenCardNumberValid)));
    }

    // Test if the support can't post a new stolen card number with an invalid card number
    @Test
    @Order(29)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPostStolenCardNumberInvalidCardNumber() throws Exception {
        String stolenCardNumberBadNumberAsJson = "{" +
                "\"number\":\"" + "1234567891234567"
                + "\"}";

        mvc
                .perform(post("/api/antifraud/stolencard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stolenCardNumberBadNumberAsJson))
                .andExpect(status().isBadRequest());
    }

    // Test if the support can get a list of stolen card numbers
    @Test
    @Order(30)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testGetStolenCardNumbersSupport() throws Exception {
        mvc
                .perform(get("/api/antifraud/stolencard"))
                .andExpect(status().isOk())
                // Response should contain an id and the number field
                .andExpect(content().string(containsString("id")))
                .andExpect(content().string(containsString("number")))
                // Response should contain the number we sent earlier
                .andExpect(content().string(containsString(stolenCardNumberValid)));
    }

    // Test if the support can delete a stolen card number
    @Test
    @Order(31)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteStolenCardNumberSupport() throws Exception {
        mvc
                .perform(delete("/api/antifraud/stolencard/" + stolenCardNumberValid))
                .andExpect(status().isOk())
                // Response should contain a status field
                .andExpect(content().string(containsString("status")))
                // Response should contain the status "Card <number> successfully removed!"
                .andExpect(content().string(containsString("Card " + stolenCardNumberValid +
                        " successfully removed!")));
    }

    // Test if the support can't delete a stolen card number with an invalid card number
    @Test
    @Order(32)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteStolenCardNumberInvalidCardNumber() throws Exception {
        mvc
                .perform(delete("/api/antifraud/stolencard/" + ""))
                .andExpect(status().isMethodNotAllowed());

        mvc
                .perform(delete("/api/antifraud/stolencard/" + cardNumberInvalid))
                .andExpect(status().isBadRequest());
    }

    // Test if the support can't delete a stolen card number with a card number that doesn't exist
    @Test
    @Order(33)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testDeleteStolenCardNumberCardNumberDoesntExist() throws Exception {
        String badCardNumberNonExisting = "1234567899876543";

        mvc
                .perform(delete("/api/antifraud/stolencard/" + badCardNumberNonExisting))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't post a new stolen card number
    @Test
    @Order(34)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPostStolenCardNumberMerchant() throws Exception {
        mvc
                .perform(post("/api/antifraud/stolencard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stolenCardNumberValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the admin can't post a new stolen card number
    @Test
    @Order(35)
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testPostStolenCardNumberAdmin() throws Exception {
        mvc
                .perform(post("/api/antifraud/stolencard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stolenCardNumberValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the support can provide feedback on a transaction
    @Test
    @Order(36)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPutFeedbackSupport() throws Exception {
        String feedbackValidAsJson = feedbackAsJsonInvalid.replace("0", String.valueOf(getLastTransactionId()));

        mvc
                .perform(put("/api/antifraud/transaction/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackValidAsJson))
                .andExpect(status().isOk())
                // Response should contain the
                // transactionId, amount, ip, number, region, date, result and feedback fields
                .andExpect(content().string(containsString("transactionId")))
                .andExpect(content().string(containsString("amount")))
                .andExpect(content().string(containsString("ip")))
                .andExpect(content().string(containsString("number")))
                .andExpect(content().string(containsString("region")))
                .andExpect(content().string(containsString("date")))
                .andExpect(content().string(containsString("result")))
                .andExpect(content().string(containsString("feedback")))
                // Response should contain the feedback we sent earlier
                .andExpect(content().string(containsString("ALLOWED")));
    }

    // Test if the support can't provide feedback on a transaction with an invalid transaction id
    @Test
    @Order(37)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPutFeedbackInvalidTransactionId() throws Exception {
        mvc
                .perform(put("/api/antifraud/transaction/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackAsJsonInvalid))
                .andExpect(status().isNotFound());
    }

    // Test if the support can't provide feedback on a transaction with an invalid feedback
    @Test
    @Order(38)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testPutFeedbackInvalidFeedback() throws Exception {
        String feedbackAsJsonBadFeedback = feedbackAsJsonInvalid
                .replace("0", String.valueOf(getLastTransactionId()))
                .replace("ALLOWED", "INVALID");

        mvc
                .perform(put("/api/antifraud/transaction/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackAsJsonBadFeedback))
                .andExpect(status().isBadRequest());
    }

    // Test if the merchant can't provide feedback on a transaction
    @Test
    @Order(39)
    @WithMockUser(username = "testmerchant", roles = {"MERCHANT"})
    void testPutFeedbackMerchant() throws Exception {
        String feedbackValidAsJson = feedbackAsJsonInvalid.replace("0", String.valueOf(getLastTransactionId()));

        mvc
                .perform(put("/api/antifraud/transaction/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the admin can't provide feedback on a transaction
    @Test
    @Order(40)
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testPutFeedbackAdmin() throws Exception {
        String feedbackValidAsJson = feedbackAsJsonInvalid.replace("0", String.valueOf(getLastTransactionId()));

        mvc
                .perform(put("/api/antifraud/transaction/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackValidAsJson))
                .andExpect(status().isForbidden());
    }

    // Test if the support can get all transactions
    @Test
    @Order(41)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testGetAllTransactionsSupport() throws Exception {
        mvc
                .perform(get("/api/antifraud/history/"))
                .andExpect(status().isOk())
                // Response should contain the
                // transactionId, amount, ip, number, region, date, result and feedback fields
                .andExpect(content().string(containsString("transactionId")))
                .andExpect(content().string(containsString("amount")))
                .andExpect(content().string(containsString("ip")))
                .andExpect(content().string(containsString("number")))
                .andExpect(content().string(containsString("region")))
                .andExpect(content().string(containsString("date")))
                .andExpect(content().string(containsString("result")))
                .andExpect(content().string(containsString("feedback")));
    }

    // Test if the support can get all transactions for a specific card number
    @Test
    @Order(42)
    @WithMockUser(username = "testsupport", roles = {"SUPPORT"})
    void testGetAllTransactionsForCardNumberSupport() throws Exception {
        mvc
                .perform(get("/api/antifraud/history/" + cardNumberValid))
                .andExpect(status().isOk())
                // Response should contain transactionId, amount, ip, number, region, date, result and feedback fields
                .andExpect(content().string(containsString("transactionId")))
                .andExpect(content().string(containsString("amount")))
                // Should contain the card number we sent earlier in test #15
                .andExpect(content().string(containsString(amountValid)))
                .andExpect(content().string(containsString("ip")))
                // Should contain the ip we sent earlier in test #15
                .andExpect(content().string(containsString(ipValid)))
                .andExpect(content().string(containsString("number")))
                // Should contain the card number we sent earlier in test #15
                .andExpect(content().string(containsString(cardNumberValid)))
                .andExpect(content().string(containsString("region")))
                // Should contain the region we sent earlier in test #15
                .andExpect(content().string(containsString(regionValid)))
                .andExpect(content().string(containsString("date")))
                // Should contain the date we sent earlier in test #15
                .andExpect(content().string(containsString(dateValid)))
                .andExpect(content().string(containsString("result")))
                // Should contain the result we sent earlier in test #34
                .andExpect(content().string(containsString("ALLOWED")))
                .andExpect(content().string(containsString("feedback")))
                // Should contain the feedback we sent earlier in test #35
                .andExpect(content().string(containsString("amount")));
    }

    // Test if a user can be deleted and clean up the database
    @Test
    @Order(43)
    @WithMockUser(username = "testadmin", roles = {"ADMINISTRATOR"})
    void testUserDeletion() throws Exception {
        mvc
                .perform(delete("/api/auth/user/" + testMerchantUsername))
                .andExpect(status().isOk())
                // Response should contain username and status fields
                .andExpect(content().string(containsString("username")))
                // Username should be the same as the one we sent earlier
                .andExpect(content().string(containsString(testMerchantUsername)))
                .andExpect(content().string(containsString("status")))
                // Status should be "Deleted successfully!"
                .andExpect(content().string(containsString("Deleted successfully!")));

        mvc
                .perform(delete("/api/auth/user/" + testSupportUsername))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("username")))
                .andExpect(content().string(containsString(testSupportUsername)))
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("Deleted successfully!")));
    }

    // Test if the latest transaction in the database is deleted (for cleanup)
    @Test
    @Order(44)
    void testTransactionDeletion() {
        // Set the latest transaction in the database as transaction id
        Long transactionId = getLastTransactionId();

        transactionRepository.deleteById(transactionId);
        assertThat(transactionRepository.findById(transactionId)).isEmpty();
    }

    // Test if the card number in the database is deleted (for cleanup)
    @Test
    @Order(45)
    void testCardNumberDeletion() {
        Optional<Card> card = cardRepository.findByNumber(cardNumberValid);

        card.ifPresent(value -> cardRepository.delete(value));
        assertThat(cardRepository.findByNumber(cardNumberValid)).isEmpty();
    }

    // Test if the admin can be removed from the database (for cleanup)
    @Test
    @Order(46)
    void removeAdminUser() {
        userService.deleteUser(testAdministrator.getUsername());
        assertThat(userRepository.findByUsername(testAdministrator.getUsername())).isNull();
    }

    /**
     * Helper method to get the latest transaction id in the database
     *
     * @return the latest transaction id as a Long
     */
    private long getLastTransactionId() {
        return transactionRepository.findAll().get(transactionRepository.findAll().size() - 1).getId();
    }
}

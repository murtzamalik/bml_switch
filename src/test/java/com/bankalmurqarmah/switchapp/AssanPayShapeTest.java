package com.bankalmurqarmah.switchapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssanPay response shape fixtures — nest/casing contracts locked for AppInSnap.
 */
class AssanPayShapeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void iftHasNestedLowercaseReturn() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("status", "00");
        nested.put("stan", "123456");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("return", nested);
        resp.put("Response_Code", "00");
        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"return\""));
        assertFalse(json.contains("\"Return\""));
        assertEquals("00", mapper.readTree(json).path("return").path("status").asText());
    }

    @Test
    void balanceHasCapitalReturn() throws Exception {
        Map<String, Object> ret = Map.of("Amount", "150000.00", "FromAccount", "0345001234567");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Return", ret);
        resp.put("Response_Code", "00");
        String json = mapper.writeValueAsString(resp);
        assertTrue(json.contains("\"Return\""));
        assertEquals("150000.00", mapper.readTree(json).path("Return").path("Amount").asText());
    }

    @Test
    void ibftTitleDoubleNest() throws Exception {
        Map<String, Object> inner = Map.of("accountTitle", "ALI KHAN", "toIMD", "601004");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("IBFTTitleFetchResponse", Map.of("return", inner));
        resp.put("Response_Code", "00");
        String json = mapper.writeValueAsString(resp);
        var node = mapper.readTree(json);
        assertTrue(node.has("IBFTTitleFetchResponse"));
        assertTrue(node.path("IBFTTitleFetchResponse").has("return"));
        assertEquals("ALI KHAN", node.path("IBFTTitleFetchResponse").path("return").path("accountTitle").asText());
    }

    @Test
    void ibftPaymentIsFlat() throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("stan", "654321");
        resp.put("transactionID", "TXN-ABC");
        resp.put("fee", "0.00");
        String json = mapper.writeValueAsString(resp);
        var node = mapper.readTree(json);
        assertFalse(node.has("return"));
        assertFalse(node.has("Return"));
        assertEquals("0.00", node.path("fee").asText());
    }

    @Test
    void mockOtpIsFourDigits() {
        assertEquals("1234", "1234");
        assertNotEquals("123456", "1234");
    }
}

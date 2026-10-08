package com.bankalmurqarmah.switchapp.adapter.imal.soap;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP SOAP client for live iMal Path Solutions.
 * Callers: SoapImalAdapter (CIF / account / transfer SOAP).
 * APIs: POST /account/open, POST /payment/ift.
 * Logs full request XML (SoapUI copy-paste) + response for bank-machine debugging without IDE.
 */
@Component
public class ImalSoapClient {
    private static final Logger log = LoggerFactory.getLogger(ImalSoapClient.class);

    private final SwitchProperties props;
    private final HttpClient http;

    public ImalSoapClient(SwitchProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(props.getImal().getTimeoutMs(), 5000)))
                .build();
    }

    public String post(String url, String soapXml, String operation) {
        String corr = MDC.get("correlationId");
        long start = System.currentTimeMillis();
        log.info("""
                
                ========== iMal SOAP REQUEST [{}] corr={} ==========
                URL: {}
                ----- copy below into SoapUI -----
                {}
                ----- end request -----
                =====================================================
                """,
                operation, corr != null ? corr : "-", url, soapXml);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(props.getImal().getTimeoutMs()))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapXml))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() != null ? response.body() : "";
            long ms = System.currentTimeMillis() - start;
            int code = response.statusCode();

            if (code >= 400) {
                log.error("""
                        
                        ========== iMal SOAP RESPONSE FAIL [{}] HTTP {} ({}ms) corr={} ==========
                        URL: {}
                        ----- response body -----
                        {}
                        ----- end response -----
                        ========================================================================
                        """,
                        operation, code, ms, corr != null ? corr : "-", url, body);
                throw new ImalSoapException(operation, "HTTP " + code, body);
            }

            boolean ok = success(body);
            String statusCode = firstTag(body, "statusCode");
            String statusDesc = firstTag(body, "statusDesc");
            if (ok) {
                log.info("""
                        
                        ========== iMal SOAP RESPONSE OK [{}] HTTP {} ({}ms) corr={} ==========
                        statusCode={} statusDesc={}
                        ----- response body (SoapUI verify) -----
                        {}
                        ----- end response -----
                        ========================================================================
                        """,
                        operation, code, ms, corr != null ? corr : "-",
                        statusCode, statusDesc, body);
            } else {
                log.error("""
                        
                        ========== iMal SOAP BUSINESS FAIL [{}] HTTP {} ({}ms) corr={} ==========
                        statusCode={} statusDesc={}
                        URL: {}
                        ----- response body -----
                        {}
                        ----- end response -----
                        (Re-send the REQUEST block above in SoapUI to reproduce)
                        ========================================================================
                        """,
                        operation, code, ms, corr != null ? corr : "-",
                        statusCode, statusDesc, url, body);
            }
            return body;
        } catch (ImalSoapException e) {
            throw e;
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("""
                    
                    ========== iMal SOAP TRANSPORT FAIL [{}] ({}ms) corr={} ==========
                    URL: {}
                    error: {}
                    ----- request that failed (SoapUI retry) -----
                    {}
                    ----- end request -----
                    ========================================================================
                    """,
                    operation, ms, corr != null ? corr : "-", url, e.getMessage(), soapXml, e);
            throw new ImalSoapException(operation,
                    e.getMessage() != null ? e.getMessage() : "SOAP call failed", null);
        }
    }

    public static String firstTag(String xml, String tag) {
        if (xml == null || tag == null) return null;
        Matcher m = Pattern.compile(
                "<(?:[\\w.-]+:)?" + Pattern.quote(tag) + ">([^<]*)</(?:[\\w.-]+:)?" + Pattern.quote(tag) + ">",
                Pattern.CASE_INSENSITIVE).matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    public static boolean success(String xml) {
        return "0".equals(firstTag(xml, "statusCode"));
    }

    public static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static class ImalSoapException extends RuntimeException {
        private final String operation;
        private final String responseXml;

        public ImalSoapException(String operation, String message, String responseXml) {
            super(message);
            this.operation = operation;
            this.responseXml = responseXml;
        }

        public String getOperation() {
            return operation;
        }

        public String getResponseXml() {
            return responseXml;
        }
    }
}

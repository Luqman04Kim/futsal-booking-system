package com.footballsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballsystem.model.Booking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

@Service
public class ToyyibPayService {

    @Value("${toyyibpay.secret-key}")
    private String secretKey;

    @Value("${toyyibpay.category-code}")
    private String categoryCode;

    @Value("${toyyibpay.base-url}")
    private String toyyibPayBaseUrl;

    @Value("${toyyibpay.app-url}")
    private String appUrl;

    private static final String CREATE_BILL_ENDPOINT = "/index.php/api/createBill";

    private RestTemplate getUnsafeRestTemplate() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                    if (connection instanceof HttpsURLConnection) {
                        HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                        httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        httpsConnection.setHostnameVerifier(new HostnameVerifier() {
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        });
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };

            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(15000);

            return new RestTemplate(requestFactory);
        } catch (Exception e) {
            System.err.println("Failed to create unsafe RestTemplate: " + e.getMessage());
            return new RestTemplate();
        }
    }

    /**
     * Creates a ToyyibPay bill and returns the redirect URL to the payment page.
     *
     * @param booking     the booking to pay for
     * @param paymentType "FULL" or "DEPOSIT"
     * @param amount      the amount to charge in RM (e.g. 150.00)
     * @return the full ToyyibPay payment URL to redirect the customer to
     */
    public String createBillAndGetUrl(Booking booking, String paymentType, double amount) throws Exception {
        RestTemplate restTemplate = getUnsafeRestTemplate();

        // ToyyibPay requires amount in CENTS (integer), e.g. RM100.50 → 10050
        long amountInCents = Math.round(amount * 100);

        // Build a unique external reference: bookingId + paymentType
        String externalRef = "FH-" + booking.getBookingId() + "-" + paymentType;

        String billName = "FootballHub Booking #" + booking.getBookingId();
        String billDesc = "Field: " + (booking.getField() != null ? booking.getField().getName() : "N/A")
                + " | Date: " + (booking.getDate() != null ? booking.getDate().toString() : "N/A")
                + " | Type: " + paymentType;

        // Return URL: user is redirected here after payment
        String returnUrl = appUrl + "/payment/return";
        // Callback URL: ToyyibPay POSTs here in the background when payment is done
        String callbackUrl = appUrl + "/payment/callback";

        // Build form-encoded body
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userSecretKey", secretKey);
        form.add("categoryCode", categoryCode);
        form.add("billName", billName);
        form.add("billDescription", billDesc);
        form.add("billPriceSetting", "1");       // 1 = fixed price
        form.add("billPayorInfo", "1");           // 1 = collect payer info
        form.add("billAmount", String.valueOf(amountInCents));
        form.add("billReturnUrl", returnUrl);
        form.add("billCallbackUrl", callbackUrl);
        form.add("billExternalReferenceNo", externalRef);
        String billTo = booking.getUser() != null ? booking.getUser().getUsername() : "Customer";
        if (billTo == null || billTo.trim().isEmpty()) {
            billTo = "Customer";
        }

        String rawEmail = booking.getUser() != null ? booking.getUser().getEmail() : null;
        String billEmail = (rawEmail != null && rawEmail.contains("@")) ? rawEmail.trim() : "customer@footballhub.com";

        String rawPhone = booking.getUser() != null ? booking.getUser().getPhoneNumber() : null;
        String billPhone = "";
        if (rawPhone != null) {
            billPhone = rawPhone.replaceAll("[^0-9]", "");
        }
        if (billPhone.isEmpty() || billPhone.length() < 9 || billPhone.length() > 13) {
            billPhone = "0123456789";
        } else {
            if (!billPhone.startsWith("60") && !billPhone.startsWith("0")) {
                billPhone = "0" + billPhone;
            }
        }

        form.add("billTo", billTo);
        form.add("billEmail", billEmail);
        form.add("billPhone", billPhone);
        form.add("billSplitPayment", "0");        // no split
        form.add("billSplitPaymentArgs", "");
        form.add("billPaymentChannel", "0");      // 0 = all channels (FPX + Card)
        form.add("billContentEmail", "Thank you for booking with FootballHub! Your booking is confirmed upon successful payment.");
        form.add("billChargeToCustomer", "0");    // 0 = charge to merchant

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        String apiUrl = toyyibPayBaseUrl + CREATE_BILL_ENDPOINT;
        System.out.println("Calling ToyyibPay API: " + apiUrl);
        System.out.println("Amount (cents): " + amountInCents + " | ExternalRef: " + externalRef);

        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
        String responseBody = response.getBody();
        System.out.println("ToyyibPay Response: " + responseBody);

        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(responseBody != null ? responseBody : "");

        String billCode = null;
        if (rootNode.isObject()) {
            if (rootNode.has("status") && "error".equals(rootNode.get("status").asText())) {
                String errMsg = rootNode.has("msg") ? rootNode.get("msg").asText() : "Unknown error";
                throw new RuntimeException("ToyyibPay error: " + errMsg);
            }
            if (rootNode.has("BillCode")) {
                billCode = rootNode.get("BillCode").asText();
            }
        } else if (rootNode.isArray() && rootNode.size() > 0) {
            com.fasterxml.jackson.databind.JsonNode firstItem = rootNode.get(0);
            if (firstItem.has("status") && "error".equals(firstItem.get("status").asText())) {
                String errMsg = firstItem.has("msg") ? firstItem.get("msg").asText() : "Unknown error";
                throw new RuntimeException("ToyyibPay error: " + errMsg);
            }
            if (firstItem.has("BillCode")) {
                billCode = firstItem.get("BillCode").asText();
            }
        }

        if (billCode == null || billCode.trim().isEmpty()) {
            throw new RuntimeException("ToyyibPay did not return a BillCode. Response: " + responseBody);
        }

        System.out.println("ToyyibPay BillCode: " + billCode);
        return toyyibPayBaseUrl + "/" + billCode;
    }
}

package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final CloseableHttpClient httpClient;
    private final Lock rateLimiterLock;
    private final long intervalMillis;
    private final int requestLimit;
    private int requestCount;
    private long lastRequestTime;
    private final String signature;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String signature) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit должен быть положительным числом");
        }

        this.requestLimit = requestLimit;
        this.intervalMillis = timeUnit.toMillis(1);
        this.httpClient = HttpClients.createDefault();
        this.rateLimiterLock = new ReentrantLock();
        this.requestCount = 0;
        this.lastRequestTime = System.currentTimeMillis();
        this.signature = signature;
        this.objectMapper = new ObjectMapper();
    }

    public void createDocument(Document document) throws Exception {
        enforceRateLimit();

        String jsonDocument = objectMapper.writeValueAsString(document);
        HttpUriRequestBase request = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");
        request.setEntity(new StringEntity(jsonDocument, ContentType.APPLICATION_JSON));
        request.setHeader("Signature", signature);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            if (statusCode != 200) {
                throw new HttpResponseException(statusCode, "API запрос завершился с ошибкой: " + response.getReasonPhrase());
            }
        }
    }

    private void enforceRateLimit() throws InterruptedException {
        rateLimiterLock.lock();
        try {
            long now = System.currentTimeMillis();
            long timeElapsed = now - lastRequestTime;

            if (timeElapsed >= intervalMillis) {
                lastRequestTime = now;
                requestCount = 0;
            }

            if (requestCount >= requestLimit) {
                long waitTime = intervalMillis - timeElapsed;
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
                lastRequestTime = System.currentTimeMillis();
                requestCount = 0;
            }

            requestCount++;
        } finally {
            rateLimiterLock.unlock();
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public static void main(String[] args) {
        try {
            // Заменить на реальную подпись для работы с API Честного знака
            String signature = "example_signature";

            CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10, signature);

            Document document = new Document();
            document.description = new Document.Description();
            document.description.participantInn = "1234567890";
            document.doc_id = "12345";
            document.doc_status = "NEW";
            document.doc_type = "LP_INTRODUCE_GOODS";
            document.importRequest = true;
            document.owner_inn = "0987654321";
            document.participant_inn = "1234567890";
            document.producer_inn = "1234567890";
            document.production_date = "2020-01-23";
            document.production_type = "MANUFACTURING";
            document.products = new Document.Product[]{
                    new Document.Product() {{
                        certificate_document = "CERT12345";
                        certificate_document_date = "2020-01-23";
                        certificate_document_number = "CERT12345";
                        owner_inn = "0987654321";
                        producer_inn = "1234567890";
                        production_date = "2020-01-23";
                        tnved_code = "12345678";
                        uit_code = "UIT123456";
                        uitu_code = "UITU123456";
                    }}
            };
            document.reg_date = "2020-01-23";
            document.reg_number = "REG12345";

            crptApi.createDocument(document);

            System.out.println("Документ успешно создан");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

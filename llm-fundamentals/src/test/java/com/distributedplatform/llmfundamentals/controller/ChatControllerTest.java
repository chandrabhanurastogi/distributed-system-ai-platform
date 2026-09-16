package com.distributedplatform.llmfundamentals.controller;

import com.distributedplatform.llmfundamentals.dto.Message;
import com.distributedplatform.llmfundamentals.dto.OllamaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ChatControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;


    @Test
    void handleChat() {

        String url = "http://localhost:" + port + "/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ==========================================
        // TURN 1: State a fact to the model
        // ==========================================
        List<Message> conversation = new ArrayList<>();
        conversation.add(new Message("user", "My favourite colour is blue. Just acknowledge that in one short sentence."));

        HttpEntity<List<Message>> turn1Request = new HttpEntity<>(conversation, headers);
        ResponseEntity<String> turn1Response = restTemplate.postForEntity(url, turn1Request, String.class);

        assertTrue(turn1Response.getBody().toLowerCase(Locale.ROOT).contains("blue"));
        String assistantReply1 = turn1Response.getBody();

// ==========================================
        // TURN 2: Resend Turn 1 + Answer + New Query
        // ==========================================
        // Explicitly tracking and appending the history to make the protocol visible
        conversation.add(new Message("assistant", assistantReply1));
        conversation.add(new Message("user", "What did I say my favourite colour was?"));

        HttpEntity<List<Message>> turn2Request = new HttpEntity<>(conversation, headers);
        ResponseEntity<String> turn2Response = restTemplate.postForEntity(url, turn2Request, String.class);
        assertTrue(turn2Response.getBody().toLowerCase().contains("blue"));

    }
}
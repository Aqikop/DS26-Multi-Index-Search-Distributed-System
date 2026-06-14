package com.example.coordinator.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.coordinator.model.UserRequest;

@Component 
public class RequestStorage {

    private final RestTemplate restTemplate;
    // private final HashSet<String> nodesList;
    private final Set<String> nodesList;
    private final ConcurrentHashMap<String, UserRequest> requestStatus;

    public RequestStorage(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // this.nodesList = new HashSet<>();
        this.nodesList = ConcurrentHashMap.newKeySet();
        this.requestStatus = new ConcurrentHashMap<>();
    }

    public UserRequest getRequest(String id) {
        return requestStatus.get(id);
    }

    public Set<String> getRequestList() {
        // ---- kduy fix from here ---
        // return requestStatus.keySet();
        return new java.util.HashSet<>(requestStatus.keySet());
        // --- to here ---
    }

    public boolean storeRequest(String id, UserRequest request) {
        try {
            requestStatus.put(id, request);
            return true;
        } catch (Exception e) {return false;}
    }

    public void deleteRequest(String id) {
        requestStatus.remove(id);
    }

    public void broadCastCopy(UserRequest request) { 
        // only leaders
        for (String node : nodesList) {
            try {
                // String targetUrl = "http://localhost:" + node + "/copy";
                String targetUrl = "http://" + node + "/copy";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<UserRequest> entity = new HttpEntity<>(request, headers);
                // ---- kduy fix from here ---
                // Boolean result = restTemplate.postForObject(targetUrl, entity, Boolean.class);
                // if (result) {System.out.println("Broadcast request done.");}
                Boolean result = restTemplate.postForObject(targetUrl, entity, Boolean.class);
                if (Boolean.TRUE.equals(result)) {System.out.println("Broadcast request done.");}
                // --- to here ---
            } catch (Exception e) {System.out.println("Broadcast request failed.");}
        }
    }

    public void addNode(String id) {
        nodesList.add(id);
    }

    public void setNode(List<String> list) {
        // ---- kduy fix from here ---
        // this.nodesList.clear();
        this.nodesList.retainAll(list);
        // --- to here ---
        this.nodesList.addAll(list);
    }

    @Scheduled(fixedDelay = 5000)
    private void cleanUp() {
        LocalDateTime now = LocalDateTime.now(); 
        // requestStatus.values().removeIf(request -> request.getTtl().isBefore(now));
        requestStatus.values().removeIf(request -> 
            request.getTtl() != null && request.getTtl().isBefore(now));
    }
}
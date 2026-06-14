package com.example.coordinator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.coordinator.model.NodesInfo;
import com.example.coordinator.model.VoteRequest;

@Component 
public class ConsensusService {
    private final RestTemplate restTemplate;
    private final ProcessingService processingService;
    private final RequestStorage storage;

    // private final HashSet<String> nodesList = new HashSet<>();
    private final Set<String> nodesList = ConcurrentHashMap.newKeySet();
    @Value("${server.port:8080}")
    private String nodeId; // do I need to sync???
    // private volatile boolean voted;
    private final AtomicBoolean voted = new AtomicBoolean(false);
    
    public volatile String nodeStatus; // do I need to sync???
    public volatile String leaderId; // do I need to sync???
    public AtomicInteger term;

    public ConsensusService(RestTemplate restTemplate, 
            ProcessingService processingService, RequestStorage storage) {
        this.restTemplate = restTemplate;
        this.processingService = processingService;
        this.storage = storage;

        // this.voted = false;
        this.voted.set(false);
        this.nodeStatus = "follower";
        this.leaderId = null;
        // this.term = 0;
        this.term = new AtomicInteger(0);
        }

    public boolean vote(VoteRequest request) { 
        int requestCount = storage.getRequestList().size();

        // ---- kduy fix from here ---
        // if (this.term <= request.getTerm() && requestCount <= request.getRequestCount()) {
        //     if(this.term < request.getTerm()) {
        //         this.term += 1;
        //         this.voted = false;
        //     }
        if (this.term.get() <= request.getTerm() && requestCount <= request.getRequestCount()) {
            if(this.term.get() < request.getTerm()) {
                this.term.set(request.getTerm());
                this.voted.set(false);
            }
        // --- to here ---
            int candidateRequestCount = request.getRequestCount();
            
            // ---- kduy fix from here ---
            // if (candidateRequestCount >= 3 && !this.voted) {
            //     this.voted = true;
            if (candidateRequestCount >= 3 && this.voted.compareAndSet(false, true)) {
            // --- to here ---
                this.nodeStatus = "follower";
                processingService.setIsLeader(false);
                return true;
                } 
            }
        return false;
    }

    public boolean ping(String id, int term) {
        // if (term >= this.term) {
        if (term >= this.term.get()) {
            this.leaderId = id;
            this.nodeStatus = "follower";
            processingService.setIsLeader(false);
            this.term.set(term);

            return true;
        } else {return false;}
    }

    public NodesInfo join(String id) {
        if (nodeStatus.equals("leader")) {
            Boolean validity = false;
            try {
                // String targetUrl = "http://localhost:" + id + "/ping";
                String targetUrl = "http://" + id + "/ping";
                String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                        .queryParam("id", nodeId)
                        // .queryParam("term", this.term)
                        .queryParam("term", this.term.get())
                        .encode()
                        .toUriString();

                validity = restTemplate.postForObject(urlTemplate, null, Boolean.class);
            } catch (RestClientException e) { System.out.println("Ping failed.");}

            if (Boolean.TRUE.equals(validity)) {
                nodesList.add(id);
                storage.addNode(id);

                for (String node : nodesList) {
                    try {
                        // String targetUrl = "http://localhost:" + id + "/join";
                        String targetUrl = "http://" + node + "/join";
                        String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                                .queryParam("id", id)
                                .encode()
                                .toUriString();
                        restTemplate.postForObject(urlTemplate, null, Boolean.class);
                    } catch (RestClientException e) { System.out.println("Ping failed.");}
                }

                // List coordinators = new ArrayList<>(nodesList);
                List<String> coordinators = new ArrayList<>(nodesList);

                coordinators.add(nodeId);
                coordinators.remove(id);

                NodesInfo info = new NodesInfo();
                info.setCoordinatorNodes(coordinators);
                info.setLlmNodes(processingService.getLlmNodes());
                info.setRecipeNodes(processingService.getDbNodes());
                return info;
            }
            return null;
        
        } else {
            nodesList.add(id);
            storage.addNode(id);
        }
        return null;
    }

    public boolean follow(String id) {
        if (id.equals(nodeId)) {return false;}
        try {
            // String targetUrl = "http://localhost:" + node + "/join";
            String targetUrl = "http://" + id + "/join";
            String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                    .queryParam("id", nodeId)
                    .encode()
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            NodesInfo info = restTemplate.postForObject(urlTemplate, entity, NodesInfo.class);
            
            if (info == null) {
                return false;
            } else {
                nodesList.clear();
                nodesList.addAll(info.getCoordinatorNodes());
                storage.setNode(info.getCoordinatorNodes());
                processingService.setLlmNodes(info.getLlmNodes());
                processingService.setDbNodes(info.getRecipeNodes());
                return true;
            }
        } catch (Exception e) {
            System.out.println("Following nodes failed.");
            return false;
        }
    }

    public boolean apply(String id, String type) {
        if (nodeStatus.equals("leader")) {
            // try service first??
            processingService.apply(id, type);
            for (String node : nodesList) {
                try {
                // String targetUrl = "http://localhost:" + node + "/apply";
                String targetUrl = "http://" + node + "/apply";
                String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                        .queryParam("id", id)
                        .queryParam("type", type)
                        .encode()
                        .toUriString();
                // return restTemplate.postForObject(urlTemplate, null, Boolean.class);
                restTemplate.postForObject(urlTemplate, null, Boolean.class);
                } catch (RestClientException e) { System.out.println("Broadcasting new worker nodes failed.");}
            }
            return true;
        } else {
            processingService.apply(id, type);
        }
        return false;
    }

    @Scheduled(fixedDelay = 1000)
    public void pingingThread() {
        if (nodeStatus.equals("leader")) {
            for (String node : nodesList) {
                try {
                    // String targetUrl = "http://localhost:" + node + "/ping";
                    String targetUrl = "http://" + node + "/ping";

                    String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                            .queryParam("id", nodeId)
                            // .queryParam("term", this.term)
                            .queryParam("term", this.term.get())
                            .encode()
                            .toUriString();

                    // ---- kduy fix from here ---
                    // Boolean response = restTemplate.postForObject(urlTemplate, null, Boolean.class);
                    // if (!response) {
                    Boolean response = restTemplate.postForObject(urlTemplate, null, Boolean.class);
                    if (response == null || !response) {
                    // --- to here ---
                        nodeStatus = "follower"; 
                        processingService.setIsLeader(false);
                        }
                } catch (Exception e) {System.out.println("Broadcast failed.");}
            }
            System.out.println("Pinging other nodes.");
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledTask() {
        // System.out.println("Current Status: " + this.nodeStatus + " " + this.term);
        System.out.println("Current Status: " + this.nodeStatus + " " + this.term.get());
        if (this.nodeStatus.equals("follower")) {
            if (this.leaderId != null) {this.leaderId = null;}
            else {
                this.nodeStatus = "candidate";
                processingService.setIsLeader(false);
                // ---- kduy fix from here ---
                // while (this.nodeStatus.equals("candidate")) {
                new Thread(this::runElection).start();
                // --- to here ---
            }
        }
    }

    private void runElection() {
        while (this.nodeStatus.equals("candidate")) {
            try {
                        long randomDelay = ThreadLocalRandom.current().nextLong(1000, 2000 + 1);
                        Thread.sleep(randomDelay);
                    } catch (InterruptedException ignore) {}

                    if (!this.nodeStatus.equals("candidate")) return;

                    // this.term = term + 1;
                    this.term.incrementAndGet();
                    // this.voted = false;
                    this.voted.set(false);
                    int vote = 1;

                    VoteRequest request = new VoteRequest();
                    request.setRequestCount(storage.getRequestList().size());
                    request.setCandidateId(this.nodeId);

                    // request.setRequestCount(4);

                    // request.setTerm(this.term);
                    request.setTerm(this.term.get());

                    // System.out.println("Start election " + this.term);
                    System.out.println("Start election " + this.term.get());

                    // check status again maybe??
                    for (String node : nodesList) {
                        try {
                            // String targetUrl = "http://localhost:" + node + "/vote";
                            String targetUrl = "http://" + node + "/vote";
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            HttpEntity<VoteRequest> entity = new HttpEntity<>(request, headers);
                            Boolean result = restTemplate.postForObject(targetUrl, entity, Boolean.class);
                            // if (result) {vote = vote + 1;}
                            if (Boolean.TRUE.equals(result)) {vote = vote + 1;}
                        } catch (RestClientException e) { System.out.println("Ask for vote failed.");}
                    }

                    if (vote > ((nodesList.size() + 1) / 2) && this.nodeStatus.equals("candidate")) {
                        this.nodeStatus = "leader";
                        processingService.setIsLeader(true);
                        processingService.processingThread(); 
                        processingService.updateQueue();
                        System.out.println("Won");
                    }
                    else {System.out.println("Lost");}
                }
    }
}
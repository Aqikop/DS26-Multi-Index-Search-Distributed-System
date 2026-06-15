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

    private final Set<String> nodesList = ConcurrentHashMap.newKeySet();
    @Value("${node.id:${server.port:8080}}")
    private String nodeId; // do I need to sync???
    private final AtomicBoolean voted = new AtomicBoolean(false);
    
    public volatile String nodeStatus; // do I need to sync???
    public volatile String leaderId; // do I need to sync???
    public AtomicInteger term;

    public ConsensusService(RestTemplate restTemplate, 
            ProcessingService processingService, RequestStorage storage) {
        this.restTemplate = restTemplate;
        this.processingService = processingService;
        this.storage = storage;

        this.voted.set(false);
        this.nodeStatus = "follower";
        this.leaderId = null;
        this.term = new AtomicInteger(0);
        }

    /**
     * Handles incoming Raft vote requests from candidates.
     * Grants vote if the candidate's term is greater or equal and it has sufficient requests.
     */
    public boolean vote(VoteRequest request) { 
        int requestCount = storage.getRequestList().size();

        if (this.term.get() <= request.getTerm() && requestCount <= request.getRequestCount()) {
            if(this.term.get() < request.getTerm()) {
                this.term.set(request.getTerm());
                this.voted.set(false);
            }
            int candidateRequestCount = request.getRequestCount();
            
            if (this.voted.compareAndSet(false, true)) {
                this.nodeStatus = "follower";
                processingService.setIsLeader(false);
                return true;
                } 
            }
        return false;
    }

    /**
     * Receives heartbeats from the Leader.
     * Resets the election timeout and updates the current term.
     */
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

    /**
     * Registers a new node to the cluster.
     * If this node is the leader, it broadcasts the new node to all followers.
     */
    public NodesInfo join(String id) {
        if (nodeStatus.equals("leader")) {
            Boolean validity = false;
            try {
                String targetUrl = formatUrl(id, "/ping");
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
                        String targetUrl = formatUrl(node, "/join");
                        String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                                .queryParam("id", id)
                                .encode()
                                .toUriString();
                        restTemplate.postForObject(urlTemplate, null, Boolean.class);
                    } catch (RestClientException e) { System.out.println("Ping failed.");}
                }

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

    /**
     * Forces this node to follow a new leader.
     */
    public boolean follow(String id) {
        if (id.equals(nodeId)) {return false;}
        try {
            String targetUrl = formatUrl(id, "/join");
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
                String targetUrl = formatUrl(node, "/apply");
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

    /**
     * Leader continuously pings followers to maintain authority.
     * Steps down to follower if a ping fails significantly (split brain prevention).
     */
    @Scheduled(fixedDelay = 1000)
    public void pingingThread() {
        if (nodeStatus.equals("leader")) {
            for (String node : nodesList) {
                try {
                    String targetUrl = formatUrl(node, "/ping");
                    String urlTemplate = UriComponentsBuilder.fromHttpUrl(targetUrl)
                            .queryParam("id", nodeId)
                            // .queryParam("term", this.term)
                            .queryParam("term", this.term.get())
                            .encode()
                            .toUriString();

                    Boolean response = restTemplate.postForObject(urlTemplate, null, Boolean.class);
                    if (response == null || !response) {
                        nodeStatus = "follower"; 
                        processingService.setIsLeader(false);
                        }
                } catch (Exception e) {System.out.println("Broadcast failed.");}
            }
            System.out.println("Pinging other nodes.");
        }
    }

    /**
     * Follower timeout checker. If no heartbeat is received from the leader
     * within the threshold, this node becomes a candidate and starts an election.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledTask() {
        // System.out.println("Current Status: " + this.nodeStatus + " " + this.term);
        System.out.println("Current Status: " + this.nodeStatus + " " + this.term.get());
        if (this.nodeStatus.equals("follower")) {
            if (this.leaderId != null) {this.leaderId = null;}
            else {
                this.nodeStatus = "candidate";
                processingService.setIsLeader(false);
                new Thread(this::runElection).start();
            }
        }
    }

    /**
     * Executes the Raft leader election process.
     * Requests votes from all known nodes and becomes Leader if the majority grants the vote.
     */
    private void runElection() {
        while (this.nodeStatus.equals("candidate")) {
            try {
                        long randomDelay = ThreadLocalRandom.current().nextLong(1000, 2000 + 1);
                        Thread.sleep(randomDelay);
                    } catch (InterruptedException ignore) {}

                    if (!this.nodeStatus.equals("candidate")) return;

                    // this.term = term + 1;
                    this.term.incrementAndGet();
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
                            String targetUrl = formatUrl(node, "/vote");
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

    private String formatUrl(String idOrAddress, String path) {
        if (idOrAddress.contains(":") || idOrAddress.contains(".") || idOrAddress.equalsIgnoreCase("localhost")) {
            return "http://" + idOrAddress + path;
        }
        return "http://localhost:" + idOrAddress + path;
    }
}
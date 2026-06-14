package com.example.coordinator.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.coordinator.model.UserRequest;
import com.example.shared.model.LLMRequest;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;

@Service
public class ProcessingService {

    private final RestTemplate restTemplate;
    private final RequestStorage storage;
    // private boolean isLeader;
    private volatile boolean isLeader;

    private final HashSet<String> llmNodes = new HashSet<>();
    private final HashSet<String> dbNodes = new HashSet<>();

    private final LinkedBlockingQueue<String> requestQueue;

    public ProcessingService(RestTemplate restTemplate, RequestStorage storage) {
        this.restTemplate = restTemplate;
        this.storage = storage;
        isLeader = false;

        this.requestQueue = new LinkedBlockingQueue<>(100);
    }

    public void setIsLeader(boolean input) {
        this.isLeader = input;
    }

    public boolean getIsLeader() {
        return this.isLeader;
    }

    public List<String> getLlmNodes() {
        return new ArrayList<>(llmNodes);
    }

    public void setLlmNodes(List<String> list) {
        this.llmNodes.clear();
        this.llmNodes.addAll(list);
    }

    public List<String> getDbNodes() {
        return new ArrayList<>(dbNodes);
    }

    public void setDbNodes(List<String> list) {
        this.dbNodes.clear();
        this.dbNodes.addAll(list);
    }

    public boolean apply(String id, String type) {
        if (type.equals("llm")) {
            llmNodes.add(id);
        } else if (type.equals("db")) {
            dbNodes.add(id);
        }
        return true;
    }

    public void updateQueue() { 
        requestQueue.addAll(storage.getRequestList());
    } 

    public boolean addToQueue(String id) {
         try {
            requestQueue.put(id);
            return true;
        } catch (Exception e) {return false;}
    }

    public void processingThread() {
        // add time out??
        Thread checkingThread = new Thread(() -> {
            while (isLeader) { 
                try {
                    String id = requestQueue.take();
                    UserRequest request = storage.getRequest(id);

                    if (request.getState().equals("received")) {
                        LLMRequest llmRequest = new LLMRequest();
                        llmRequest.setUserQuery(request.getUserQuery());
                        RecipeQuery result = sendToLLMNode(llmRequest);

                        if (result != null) {
                            request.setState("formatted");
                            request.setRecipeQuery(result);
                            storage.storeRequest(id, request);
                            this.addToQueue(id);
                            storage.broadCastCopy(request);
                        } else {
                            System.out.println("LLM decompose failed for " + id + ". Retrying in 5s...");
                            try { Thread.sleep(5000); } catch (Exception ignored) {}
                            this.addToQueue(id);
                        }

                    } else if (request.getState().equals("formatted")) {
                        RecipeQuery recipeQuery = request.getRecipeQuery();
                        List<RecipeQueryResult> result = sendToDBNode(recipeQuery);

                        // ---- kduy fix here ---
                        // if (result != null) {
                        //     request.setState("unformatted results");
                        //     request.setRecipeQueryResults(result);
                        //     storage.storeRequest(id, request);
                        //     this.addToQueue(id);
                        //     storage.broadCastCopy(request);
                        // } else {
                        //     System.out.println("No result, request failed.");
                        //     //storage.deleteRequest(id);
                        // }
                        // 
                        // request.setState("unformatted result");
                        // storage.storeRequest(id, request);
                        // this.addToQueue(id);
                        // storage.broadCastCopy(request);
                        // 
                        // } else if (request.getState().equals("unformatted result")) {
                        if (result != null) {
                            request.setState("searched");
                            request.setRecipeQueryResults(result);
                            storage.storeRequest(id, request);
                            this.addToQueue(id);
                            storage.broadCastCopy(request);
                        } else {
                            System.out.println("Recipe DB search failed for " + id + ". Retrying in 5s...");
                            try { Thread.sleep(5000); } catch (Exception ignored) {}
                            this.addToQueue(id);
                        }

                    } else if (request.getState().equals("searched")) {
                        // --- kduy fix here ----

                        // get the llm node to process
                        // added all the new result back to request

                        // Thread.sleep(5000); 
                        // request.setState("done");
                        // request.setResult("final result is here");
                        // storage.storeRequest(id, request);
                        // storage.broadCastCopy(request);
                        
                        String finalResult = sendToLLMAnswerNode(request);
                        if (finalResult != null) {
                            request.setState("done");
                            request.setResult(finalResult);
                            storage.storeRequest(id, request);
                            storage.broadCastCopy(request);
                        } else {
                            System.out.println("LLM answer generation failed for " + id + ". Retrying in 5s...");
                            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                            this.addToQueue(id);
                        }
                        // ---- to here kduy ---
                        
                    } else {
                        System.out.println("Something went very wrong");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("The sleep was interrupted.");
                } catch (Exception e) {
                    System.out.println("The node cannot be called");
                    e.printStackTrace();
                }
            }
        });

        checkingThread.setDaemon(true); 
        checkingThread.start();
    }

    private RecipeQuery sendToLLMNode(LLMRequest llmRequest) {
        int numberOfNodes = llmNodes.size();
        if (numberOfNodes > 0)  {
            int attempt = 0;
            do {
                attempt = attempt + 1;
                String node = (String) llmNodes.toArray()[new Random().nextInt(numberOfNodes)];
                try {
                    // String targetUrl = "http://" + node + "/llm";
                    // String targetUrl = "http://localhost:" + node + "/llm/decompose";
                    String targetUrl = "http://" + node + "/llm/decompose";

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<LLMRequest> entity = new HttpEntity<>(llmRequest, headers);

                    return restTemplate.postForObject(targetUrl, entity, RecipeQuery.class);
                } catch (Exception e) {System.out.println("Calling llm service failed.");}
            } while (attempt < 5);
            return null;
        } else {
            System.out.println("No llm nodes found");
            return null;
        }
    }
    
    private List<RecipeQueryResult> sendToDBNode(RecipeQuery recipeQuery) {
        int numberOfNodes = dbNodes.size();
        List<RecipeQueryResult> results = new ArrayList<>();
        if (numberOfNodes >= 0)  {
            for (String node : dbNodes) {
                try {
                    // String targetUrl = "http://localhost:" + node + "/recipes/search";
                    String targetUrl = "http://" + node + "/recipes/search";
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<RecipeQuery> entity = new HttpEntity<>(recipeQuery, headers);

                    ResponseEntity<List<RecipeQueryResult>> response = restTemplate.exchange(
                            targetUrl, HttpMethod.POST, entity,
                            new ParameterizedTypeReference<List<RecipeQueryResult>>() {}
                    );

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        results.addAll(response.getBody());
                    }
                } catch (Exception e) {System.out.println("Calling RECIPE NODE service failed.");}
            }
            // ---- kduy add here ---
            // add sort top10 only
            results.sort((r1, r2) -> {
                Double s1 = r1.getScore() != null ? r1.getScore() : 0.0;
                Double s2 = r2.getScore() != null ? r2.getScore() : 0.0;
                return Double.compare(s2, s1);
            });
            if (results.size() > 10) {
                return new ArrayList<>(results.subList(0, 10));
            }
            return results;
            // --- to here kduy ----
        } else {
            System.out.println("No recipe nodes found");
            return null;
        }
    }

    private String sendToLLMAnswerNode(UserRequest userRequest) {
        int numberOfNodes = llmNodes.size();
        if (numberOfNodes > 0)  {
            int attempt = 0;
            do {
                attempt = attempt + 1;
                String node = (String) llmNodes.toArray()[new Random().nextInt(numberOfNodes)];
                try {
                    // String targetUrl = "http://localhost:" + node + "/llm/answer";
                    String targetUrl = "http://" + node + "/llm/answer";
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<UserRequest> entity = new HttpEntity<>(userRequest, headers);

                    return restTemplate.postForObject(targetUrl, entity, String.class);
                } catch (Exception e) {
                    System.out.println("Calling llm service failed.");
                    e.printStackTrace();
                }
            } while (attempt < 5);
            return null;
        } else {
            System.out.println("No llm nodes found");
            return null;
        }
    }
}
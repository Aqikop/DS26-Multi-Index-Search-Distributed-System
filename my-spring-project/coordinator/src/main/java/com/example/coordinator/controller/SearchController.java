package com.example.coordinator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.coordinator.model.NodesInfo;
import com.example.coordinator.model.UserRequest;
import com.example.coordinator.model.VoteRequest;
import com.example.coordinator.service.ConsensusService;
import com.example.coordinator.service.CoordinatorService;
import com.example.coordinator.service.ProcessingService;
import com.example.coordinator.service.RequestStorage;
import com.example.shared.model.LLMRequest;


@RestController
@RequestMapping("/")
public class SearchController {

    private final CoordinatorService coordinatorService;
    private final ConsensusService consensus;
    private final ProcessingService processingService;
    private final RequestStorage storage;

    public SearchController(CoordinatorService coordinatorService, ConsensusService consensus, 
            ProcessingService processingService, RequestStorage storage) {
        this.coordinatorService = coordinatorService;
        this.consensus = consensus;
        this.processingService = processingService;
        this.storage = storage;
    }

    @PostMapping("/search")
    public String search(@RequestBody LLMRequest request) {
        return coordinatorService.search(request);
    }

    @GetMapping("/get")
    public String get(@RequestParam String id) {
        return coordinatorService.get(id);
    } 

    @GetMapping("/gettest") // just for test
    public UserRequest getTest(@RequestParam String id) {
        return coordinatorService.getTest(id);
    }



    @PostMapping("/copy") 
    public boolean copy(@RequestBody UserRequest request) { 
        storage.storeRequest(request.getId(), request);
        return true;
    }

    @PostMapping("/ping") 
    public boolean ping(@RequestParam String id, @RequestParam int term) { 
        return consensus.ping(id, term);
    }

    @PostMapping("/vote") 
    public boolean vote(@RequestBody VoteRequest request) { 
        return consensus.vote(request);
    }

    @PostMapping("/join") 
    public NodesInfo join(@RequestParam String id) { 
        return consensus.join(id);
    }

    @PostMapping("/apply") 
    public boolean apply(@RequestParam String id, @RequestParam String type) { 
        return consensus.apply(id, type);
    }

    // ---- kduy fix from here ---
    // @PostMapping("/follow") 
    // public boolean apply(@RequestParam String id) { 
    @PostMapping("/follow") 
    public boolean follow(@RequestParam String id) { 
    // --- to here ---
        return consensus.follow(id);
    }
}
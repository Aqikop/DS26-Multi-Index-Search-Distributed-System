package com.example.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserRequest {
    private String id;
    private StatusState state;
    private String userQuery;
    private String queriesForDb;
    private String listOfResult;
    private String result;

    public UserRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public StatusState getState() { return state; }
    public void setState(StatusState state) { this.state = state; }

    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }

    public String getQueriesForDb() { return queriesForDb; }
    public void setQueriesForDb(String queriesForDb) { this.queriesForDb = queriesForDb; }

    public String getListOfResult() { return listOfResult; }
    public void setListOfResult(String listOfResult) { this.listOfResult = listOfResult; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
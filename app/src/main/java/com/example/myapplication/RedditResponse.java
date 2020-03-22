package com.example.myapplication;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;

public class RedditResponse {
    @SerializedName("kind") public String kind;
    @SerializedName("data") JsonElement data;
}

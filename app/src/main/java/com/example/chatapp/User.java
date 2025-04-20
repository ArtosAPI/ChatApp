package com.example.chatapp;

import java.util.HashMap;
import java.util.Map;

public class User {
    public String email;
    public String nickname;
    public Map<String, Boolean> chats = new HashMap<>();

    public User() {}

    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }
}
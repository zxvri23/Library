package com.tuvarna.bg.library.util;

public class ActivityRow {
    private final String date, action, user, details;
    public ActivityRow(String d, String a, String u, String de) {
        this.date = d; this.action = a; this.user = u; this.details = de;
    }
    public String getDate() { return date; }
    public String getAction() { return action; }
    public String getUser() { return user; }
    public String getDetails() { return details; }
}

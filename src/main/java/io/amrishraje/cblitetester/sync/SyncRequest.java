package io.amrishraje.cblitetester.sync;

import java.util.List;

public class SyncRequest {
    private String user;
    private String password;
    private List<String> channels;

    public SyncRequest(String user, String password, List<String> channels) {
        this.user = user;
        this.password = password;
        this.channels = channels;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getChannels() {
        return channels;
    }
}

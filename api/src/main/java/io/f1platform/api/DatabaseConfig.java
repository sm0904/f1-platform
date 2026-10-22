package io.f1platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Database settings. The password comes from the environment, never from the YAML file. */
public class DatabaseConfig {

    @JsonProperty
    private String url = "jdbc:postgresql://localhost:5432/f1platform";

    @JsonProperty
    private String user = "f1";

    @JsonProperty
    private String password = "";

    @JsonProperty
    private int maximumPoolSize = 10;

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

package com.eventsystem.infrastructure.persistence.entities;





import jakarta.persistence.Embeddable;

@Embeddable
public class HashedCredentialsEmbeddable {

    private String hash;
    private String salt;
    private String algorithm;

    protected HashedCredentialsEmbeddable() {}

    public HashedCredentialsEmbeddable(String hash, String salt, String algorithm) {
        this.hash = hash;
        this.salt = salt;
        this.algorithm = algorithm;
    }

    public String getHash() {
        return hash;
    }

    public String getSalt() {
        return salt;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    // getters
}
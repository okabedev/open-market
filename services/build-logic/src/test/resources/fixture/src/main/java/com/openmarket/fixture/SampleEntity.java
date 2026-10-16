package com.openmarket.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
public class SampleEntity {

    @Id
    private UUID id;

    private String name;

    private @Nullable String memo;

    protected SampleEntity() {}

    public SampleEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getMemo() {
        return memo;
    }
}

package com.example.saasguide.communication.application.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryApplicationRepository {
    private final Map<String, ApplicationRecord> records = new ConcurrentHashMap<>();

    public void save(ApplicationRecord record) {
        records.put(record.applicationId(), record);
    }

    public int size() {
        return records.size();
    }
}

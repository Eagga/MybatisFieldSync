package com.eagga.mybatisfieldsync.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldSyncServiceTest {

    @Test
    void shouldPrioritizeSameModuleThenDependenciesThenOthers() {
        List<Candidate> sorted = FieldSyncService.sortByModulePriority(
                List.of(
                        new Candidate("shared-api", "/repo/shared-api/src/main/resources/UserMapper.xml"),
                        new Candidate("billing-app", "/repo/billing-app/src/main/resources/UserMapper.xml"),
                        new Candidate("legacy-app", "/repo/legacy-app/src/main/resources/UserMapper.xml"),
                        new Candidate("billing-app", "/repo/billing-app/src/main/resources/ZUserMapper.xml")),
                Candidate::moduleName,
                Candidate::path,
                "billing-app",
                Set.of("shared-api", "shared-db"));

        assertEquals(List.of(
                        "/repo/billing-app/src/main/resources/UserMapper.xml",
                        "/repo/billing-app/src/main/resources/ZUserMapper.xml",
                        "/repo/shared-api/src/main/resources/UserMapper.xml",
                        "/repo/legacy-app/src/main/resources/UserMapper.xml"),
                sorted.stream().map(Candidate::path).toList());
    }

    @Test
    void shouldFallbackToPathOrderWhenNoModuleContextExists() {
        List<Candidate> sorted = FieldSyncService.sortByModulePriority(
                List.of(
                        new Candidate("", "/repo/b/UserMapper.xml"),
                        new Candidate("", "/repo/a/UserMapper.xml")),
                Candidate::moduleName,
                Candidate::path,
                null,
                Set.of());

        assertEquals(List.of("/repo/a/UserMapper.xml", "/repo/b/UserMapper.xml"),
                sorted.stream().map(Candidate::path).toList());
    }

    private record Candidate(String moduleName, String path) {
    }
}

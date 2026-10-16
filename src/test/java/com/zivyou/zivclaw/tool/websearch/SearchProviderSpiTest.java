package com.zivyou.zivclaw.tool.websearch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchProviderSpiTest {

    @Test
    void serviceLoaderDiscoversDoubaoProvider() {
        List<SearchProvider> providers = ServiceLoader.load(SearchProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertTrue(providers.stream().anyMatch(p -> DoubaoSearchProvider.NAME.equals(p.name())),
                "META-INF/services 应声明 DoubaoSearchProvider");
    }

    @Test
    void webSearchSelectsProviderByConfiguredName() {
        // application.yaml 中 app.webSearch.providerName=doubao
        WebSearch tool = new WebSearch();
        assertEquals(DoubaoSearchProvider.NAME, tool.searchProvider.name());
    }
}

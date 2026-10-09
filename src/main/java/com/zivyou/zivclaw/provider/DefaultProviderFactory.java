package com.zivyou.zivclaw.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultProviderFactory {
    public static Provider getProvider() {
        try (var provider = new OrcaRouterProvider()) {
            return provider;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        try (var provider = new ArkProvider()) {
            return provider;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        throw new RuntimeException("no valid provider");
    }
}

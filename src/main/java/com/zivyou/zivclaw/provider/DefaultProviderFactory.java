package com.zivyou.zivclaw.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultProviderFactory {

    public static Provider getProvider() {
        return new ArkProvider();
    }


}

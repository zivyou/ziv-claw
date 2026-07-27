package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Function {
    String name;
    String arguments;
}

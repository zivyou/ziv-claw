package com.zivyou.zivclaw.message;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Function {
    String name;
    String arguments;
}

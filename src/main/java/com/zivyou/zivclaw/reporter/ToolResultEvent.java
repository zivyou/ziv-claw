package com.zivyou.zivclaw.reporter;

import com.zivyou.zivclaw.registry.ToolResult;

/**
 * 工具执行结果事件的载体。因为工具是并行执行的,result 由哪个工具产物携带、
 * 名字是什么必须打包下发,Presenter 才能展示"◀ 工具 &lt;name&gt; 成功/失败"。
 *
 * @param toolName 触发该调用结果所属工具的注册名
 * @param result   工具执行结果(含返回 output 与成功/失败标记)
 */
public record ToolResultEvent(String toolName, ToolResult result) {
    public boolean isError() {
        return result.isError();
    }

    public String output() {
        return result.getOutput();
    }
}
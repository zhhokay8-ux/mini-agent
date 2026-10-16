package com.miniagent.chat;

public record ToolTrace(int round, String name, String arguments, long durationMs, boolean success) {}

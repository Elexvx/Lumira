package com.lumira.common.web;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    public static void setSpanId(String spanId) {
        SPAN_ID_HOLDER.set(spanId);
    }

    public static String getSpanId() {
        return SPAN_ID_HOLDER.get();
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID_HOLDER.set(requestId);
    }

    public static String getRequestId() {
        return REQUEST_ID_HOLDER.get();
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        SPAN_ID_HOLDER.remove();
        REQUEST_ID_HOLDER.remove();
    }
}

package lk.ac.sliit.tgms.api;

import java.util.Map;

public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message, Map.of()));
    }

    public static ApiErrorResponse withFields(
            String code, String message, Map<String, String> fields) {
        return new ApiErrorResponse(new ApiError(code, message, Map.copyOf(fields)));
    }

    public record ApiError(String code, String message, Map<String, String> fields) {}
}

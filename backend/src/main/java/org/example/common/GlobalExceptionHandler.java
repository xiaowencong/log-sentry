package org.example.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleIllegalState(IllegalStateException e) {
        log.warn("非法状态请求: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDateTimeParse(DateTimeParseException e) {
        return Result.error(400, "时间格式错误，请使用 yyyy-MM-ddTHH:mm:ss 格式");
    }

    /**
     * 兜底异常处理：捕获所有未处理的异常，返回统一 JSON 格式，避免 Spring Boot 默认错误页面。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未捕获异常: {}", e.getMessage(), e);
        return Result.error(500, "服务器内部错误");
    }
}

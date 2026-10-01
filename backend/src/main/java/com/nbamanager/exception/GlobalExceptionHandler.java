package com.nbamanager.exception;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return body(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return body(HttpStatus.FORBIDDEN, "权限不足");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", HttpStatus.BAD_REQUEST.value());
        body.put("message", "参数校验失败");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "头像大小不能超过 2MB");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        return body(HttpStatus.CONFLICT, "存在关联数据或约束冲突，无法完成操作");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "资源正在被并发修改，请稍后重试");
    }

    // 客户端用法错误：按语义返回 4xx，而不是笼统的 500（此前会被兜底成"服务器内部错误"）
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return body(HttpStatus.BAD_REQUEST, "缺少必要参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleParamType(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "参数类型错误: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法: " + ex.getMethod());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        // 业务校验常用 IllegalArgumentException：返回 400 而非被兜底成 500
        log.warn("非法参数: {}", ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, ex.getMessage() == null ? "参数错误" : ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return body(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason() == null ? ex.getMessage() : ex.getReason());
    }

    /**
     * 静态资源缺失应返回 404。
     *
     * 此前 /uploads/** 下不存在的文件会抛 NoResourceFoundException，
     * 由于没有对应 @ExceptionHandler，被下面的 Exception.class 兜底成
     * 500 "服务器内部错误"（安全报告 §九 S4-R1）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "资源不存在");
    }
    /**
     * 非法请求（Spring Security 的 StrictHttpFirewall 拒绝）。返回 400 而不是 500。
     *
     * 安全报告 §十 10.5：`GET /api/posts?page=0&size%00=10` 会被防火墙以
     * "parameter name is not allowed" 拒绝；此前没有专属处理器，被兜底成 500，
     * 属可被外部轻易构造的 5xx。
     */
    @ExceptionHandler(org.springframework.security.web.firewall.RequestRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleRequestRejected(
            org.springframework.security.web.firewall.RequestRejectedException ex) {
        log.warn("请求被安全防火墙拒绝: {}", ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "请求包含非法参数");
    }

    /**
     * multipart 解析失败（非 multipart 打上传端点、filename 含 NUL 等）。返回 400。
     *
     * 安全报告 §十 10.11：这类请求此前一律 500。
     * 注意 {@link MaxUploadSizeExceededException} 是 MultipartException 的子类，
     * 上面的 413 处理器仍优先（Spring 选最具体匹配），这里再显式判断一次以防顺序变化。
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return body(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大");
        }
        log.warn("multipart 请求解析失败: {}", ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "上传请求格式错误");
    }

    /**
     * Content-Type 不受支持（如 JSON 端点收到 application/x-www-form-urlencoded）。
     * 语义上应返回 415 而不是 500。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("不支持的 Content-Type: {}", ex.getContentType());
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求内容类型");
    }

    /**
     * 请求处理过程中的非法状态。典型是"上传解析中重复读取文件"
     * （StandardMultipartHttpServletRequest: 文件解析进行中）。返回 409。
     *
     * 注意：这里只处理上传解析相关的 IllegalStateException，
     * 其它语义的 IllegalStateException 仍按 409（客户端可重试/修正请求）返回，
     * 不会退化成 500。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("请求处理状态非法: {}", ex.getMessage());
        return body(HttpStatus.CONFLICT, "请求处理状态冲突，请重试");
    }
    /**
     * 响应写出阶段的 IOException。
     *
     * 典型来源是我们主动中断超长请求（SlowRequestGuardFilter 到点后 interrupt 工作线程）
     * 或客户端提前断开：此时容器 flush 会失败并抛 IOException。这类情况<b>不是服务端故障</b>，
     * 若按兜底的 ERROR + 堆栈记录，会在压测/慢客户端场景下刷出大量误导性的"未处理异常"，
     * 把真正的错误淹没。因此对"线程被中断 / 客户端断连"降级为单行 WARN；
     * 其余 IO 异常仍按 500 走 ERROR，保持原有可见性。
     */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Map<String, Object>> handleIo(java.io.IOException ex) {
        if (isInterruptOrClientAbort(ex)) {
            log.warn("响应写出被中断（主动超时或客户端断开）: {}", ex.getMessage());
            return body(HttpStatus.REQUEST_TIMEOUT, "请求已中断");
        }
        log.error("响应写出失败: {}", ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private static boolean isInterruptOrClientAbort(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("interrupted")
                    || msg.contains("Connection reset")
                    || msg.contains("Broken pipe")
                    || msg.contains("aborted"))) {
                return true;
            }
            if (t.getClass().getName().contains("ClientAbortException")) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return Thread.currentThread().isInterrupted();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("未处理异常: {}", ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", status.value());
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}

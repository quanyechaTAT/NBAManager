package com.nbamanager.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * F2 回归：静态资源缺失必须是 404，而不是 500。
     *
     * 安全报告 §九 S4-R1：GET /uploads/chat/{gameId}/does-not-exist.png 返回 500，
     * 因为 NoResourceFoundException 没有被映射，被 Exception 兜底成了服务器内部错误。
     */
    @Test
    void missingStaticResourceReturnsNotFoundInsteadOfInternalServerError() {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "chat/401881722/does-not-exist.png");

        ResponseEntity<Map<String, Object>> response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", HttpStatus.NOT_FOUND.value());
    }

    @Test
    void optimisticLockingFailureReturnsConflictInsteadOfInternalServerError() {
        ResponseEntity<Map<String, Object>> response = handler.handleOptimisticLocking(
                new OptimisticLockingFailureException("stale like row"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("code", HttpStatus.CONFLICT.value())
                .containsEntry("message", "资源正在被并发修改，请稍后重试");
    }
    // ==================== F2 回归：框架异常必须映射为 4xx，不得兜底成 500 ====================

    /**
     * 安全报告 §十 10.5 / 10.11：`?size%00=10` 被 StrictHttpFirewall 拒绝，
     * 抛出的 RequestRejectedException 没有专属处理器，此前被兜底成 500。
     */
    @Test
    void requestRejectedByFirewallReturnsBadRequestInsteadOfInternalServerError() {
        var ex = new org.springframework.security.web.firewall.RequestRejectedException(
                "The request was rejected because the parameter name \"size\" is not allowed.");

        ResponseEntity<Map<String, Object>> response = handler.handleRequestRejected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", HttpStatus.BAD_REQUEST.value());
    }

    /**
     * 非 multipart 请求打上传端点：MultipartException -> 400。
     */
    @Test
    void nonMultipartRequestReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleMultipart(
                new MultipartException("Current request is not a multipart request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", HttpStatus.BAD_REQUEST.value());
    }

    /**
     * multipart 的 filename 含 NUL 时 Tomcat 抛 MultipartException -> 400。
     */
    @Test
    void malformedMultipartFilenameReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleMultipart(
                new MultipartException("Failed to parse multipart servlet request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * 关键回归：MaxUploadSizeExceededException 是 MultipartException 的子类，
     * 必须仍然返回 413（超 2MB 头像/5MB 聊天图不得退化成 400）。
     */
    @Test
    void maxUploadSizeStillReturnsPayloadTooLargeEvenThoughItExtendsMultipartException() {
        assertThat(MaxUploadSizeExceededException.class)
                .isAssignableTo(MultipartException.class);

        ResponseEntity<Map<String, Object>> response = handler.handleMultipart(
                new MaxUploadSizeExceededException(2 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * JSON 端点收到 form 编码：HttpMediaTypeNotSupportedException -> 415。
     */
    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() {
        ResponseEntity<Map<String, Object>> response = handler.handleMediaType(
                new HttpMediaTypeNotSupportedException("application/x-www-form-urlencoded"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).containsEntry("code", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    }

    /**
     * 上传解析中重复读取文件：IllegalStateException -> 409（而非 500）。
     */
    @Test
    void illegalStateDuringRequestProcessingReturnsConflict() {
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(
                new IllegalStateException("文件解析进行中"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", HttpStatus.CONFLICT.value());
    }
}
package com.enosiot.enos.iot_http_integration;

import static com.enosiot.enos.iot_http_integration.HttpConnectionError.*;
import static com.enosiot.enos.iot_mqtt_sdk.core.internals.constants.FormDataConstants.ENOS_MESSAGE;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.enosiot.enos.iot_http_integration.message.*;
import com.enosiot.enos.iot_http_integration.progress.IProgressListener;
import com.enosiot.enos.iot_http_integration.progress.ProgressRequestWrapper;
import com.enosiot.enos.iot_http_integration.utils.FileUtil;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.iot_mqtt_sdk.core.internals.constants.RangeFileBody;
import com.enosiot.enos.iot_mqtt_sdk.message.upstream.tsl.UploadFileInfo;
import com.enosiot.enos.iot_mqtt_sdk.util.GsonUtil;
import com.enosiot.enos.iot_mqtt_sdk.util.StringUtil;
import com.enosiot.enos.sdk.data.DeviceInfo;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Monitor;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author :charlescai
 * @date :2020-02-18
 */
@Slf4j
public class HttpConnection {
    private static final String VERSION = "1.1";

    private static final String INTEGRATION_PATH = "/connect-service/v2.1/integration";
    private static final String FILES_PATH = "/connect-service/v2.1/files";
    private static final String FILES_V25_PATH = "/connect-service/v2.5/files";

    private static final String APIM_ACCESS_TOKEN = "apim-accesstoken";
    private static final String RANGE = "Range";

    /**
     * Builder for http connection. A customized OkHttpClient can be provided, to define specific
     * connection pool, proxy etc. Find more at {@link #okHttpClient}
     *
     * @author cai.huang
     */
    @Data
    public static class Builder {
        @NonNull private String integrationBrokerUrl;

        @NonNull private String tokenServerUrl;

        @NonNull private String appKey;

        @NonNull private String appSecret;

        @NonNull private String orgId;

        private OkHttpClient okHttpClient;

        private boolean useLark = false;

        private boolean autoUpload = true;

        public HttpConnection build() throws EnosException {
            HttpConnection instance = new HttpConnection();

            instance.integrationBrokerUrl = integrationBrokerUrl;

            instance.orgId = orgId;

            // allocate client
            if (okHttpClient == null) {
                okHttpClient =
                        new OkHttpClient.Builder()
                                .connectTimeout(10L, TimeUnit.SECONDS)
                                .readTimeout(2L, TimeUnit.MINUTES)
                                .writeTimeout(2L, TimeUnit.MINUTES)
                                .retryOnConnectionFailure(true)
                                .build();
            }
            instance.okHttpClient = okHttpClient;

            // construct token connection
            instance.tokenConnection =
                    TokenConnection.builder()
                            .tokenServerUrl(tokenServerUrl)
                            .appKey(appKey)
                            .appSecret(appSecret)
                            .okHttpClient(instance.okHttpClient)
                            .build();

            // initiate token
            instance.tokenConnection.getAndRefreshToken();

            instance.setAutoUpload(this.autoUpload);
            instance.setUseLark(this.useLark);

            return instance;
        }

        public Builder setUseLark(boolean useLark) {
            this.useLark = useLark;
            return this;
        }

        public Builder setAutoUpload(boolean autoUpload) {
            this.autoUpload = autoUpload;
            return this;
        }
    }

    private String integrationBrokerUrl;

    private String orgId;

    // For automatic apply for access token
    private Monitor authMonitor = new Monitor();

    private TokenConnection tokenConnection;

    @Getter private OkHttpClient okHttpClient = null;

    @Getter @Setter private boolean autoUpload = true;

    @Getter @Setter private boolean useLark = false;

    /** A sequence ID used in request */
    @Getter @Setter private AtomicInteger seqId = new AtomicInteger(0);

    // ======== auth via token server will be automatically executed =========

    private void checkAuth() throws EnosException {
        if (tokenConnection.needGetToken() || tokenConnection.needRefreshToken()) {
            auth();
        }
    }

    /**
     * Ensure to get / refresh access token
     *
     * @throws EnosException with code {@code UNSUCCESSFUL_AUTH} if failed to get access token
     */
    public void auth() throws EnosException {
        if (authMonitor.tryEnter()) {
            try {
                // if there is no accessToken, you need to get token
                // or if token is near to expiry, you need to refresh token
                if (tokenConnection.needGetToken()) {
                    tokenConnection.getToken();
                } else if (tokenConnection.needRefreshToken()) {
                    tokenConnection.refreshToken();
                }
            } finally {
                authMonitor.leave();
            }
        } else if (authMonitor.enter(10L, TimeUnit.SECONDS)) {
            // Wait at most 10 seconds and try to get Access Token
            try {
                if (tokenConnection.needGetToken()) {
                    throw new EnosException(UNSUCCESSFUL_AUTH);
                }
            } finally {
                authMonitor.leave();
            }
        }
    }

    // =======================================================================

    /**
     * Publish a request to EnOS IOT HTTP broker
     *
     * <p>Response
     *
     * @param request
     * @param progressListener used to handle file uploading progress, {@code null} if not available
     * @return response
     * @throws EnosException
     * @throws IOException
     */
    public IntegrationResponse publish(
            BaseIntegrationRequest request, IProgressListener progressListener)
            throws EnosException, IOException {
        Call call = generatePublishCall(request, request.getFiles(), progressListener);
        IntegrationResponse integrationResponse = publishCall(call, IntegrationResponse.class);
        if (this.isUseLark() && request.getFiles() != null) {
            uploadFileByUrl(request, integrationResponse);
        }
        return integrationResponse;
    }

    /**
     * Publish a request to EnOS IOT HTTP broker, asynchronously with a callback
     *
     * @param request
     * @param callback
     * @param progressListener used to handle file uploading progress, {@code null} if not available
     * @throws IOException
     * @throws EnosException
     */
    public void publish(
            BaseIntegrationRequest request,
            IIntegrationCallback callback,
            IProgressListener progressListener)
            throws IOException, EnosException {
        Call call = generatePublishCall(request, request.getFiles(), progressListener);
        publishCallAsync(call, callback);
    }

    /**
     * Delete a file
     *
     * @param deviceInfo
     * @param fileUri
     * @return
     * @throws EnosException
     */
    public IntegrationResponse deleteFile(DeviceInfo deviceInfo, String fileUri)
            throws EnosException {
        Call call = generateDeleteCall(orgId, deviceInfo, fileUri);
        return publishCall(call, IntegrationResponse.class);
    }

    /**
     * Delete a file async
     *
     * @param deviceInfo
     * @param fileUri
     * @param callback
     * @throws EnosException
     */
    public void deleteFile(DeviceInfo deviceInfo, String fileUri, IIntegrationCallback callback)
            throws EnosException {
        Call call = generateDeleteCall(orgId, deviceInfo, fileUri);
        publishCallAsync(call, callback);
    }

    public FileInfoResponse getFileInfo(
            DeviceInfo deviceInfo, String fileUri, FileCategory category) throws EnosException {
        Call call = generateGetFileInfoCall(orgId, category, deviceInfo, fileUri);
        return publishCall(call, FileInfoResponse.class);
    }

    //    异步方式没必要，先注释掉
    //    public void getFileInfo(DeviceInfo deviceInfo, String fileUri, FileCategory category,
    // IFileInfoCallBack callback) throws EnosException {
    //        Call call = generateGetFileInfoCall(orgId, category, deviceInfo, fileUri);
    //        publishCallAsync(call, callback);
    //    }

    public RangeFileBody downloadFile(
            DeviceInfo deviceInfo,
            String fileUri,
            FileCategory category,
            Long startRange,
            Long endRange)
            throws EnosException, IOException {
        RangeFileBody.RangeFileBodyBuilder builder = RangeFileBody.builder();

        Call call =
                generateFileDownloadCall(
                        orgId, deviceInfo, fileUri, category, startRange, endRange);
        Response httpResponse;
        try {
            httpResponse = call.execute();

            if (!httpResponse.isSuccessful()) {
                throw new EnosException(httpResponse.code(), httpResponse.message());
            }

            try {
                Preconditions.checkNotNull(httpResponse);
                Preconditions.checkNotNull(httpResponse.body());

                return builder.contentLength(
                                Integer.parseInt(httpResponse.headers().get("Content-length")))
                        .contentRange(httpResponse.headers().get("Content-Range"))
                        .acceptRanges(httpResponse.headers().get("Accept-Ranges"))
                        .data(httpResponse.body().byteStream())
                        .build();
            } catch (Exception e) {
                log.info("failed to get response: " + httpResponse, e);
                throw new EnosException(CLIENT_ERROR);
            }
        } catch (SocketException e) {
            log.info("failed to execute request due to socket error {}", e.getMessage());
            throw new EnosException(SOCKET_ERROR, e.getMessage());
        } catch (EnosException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to execute request", e);
            throw new EnosException(CLIENT_ERROR);
        }
    }

    /**
     * Download file
     *
     * @param deviceInfo
     * @param fileUri
     * @return
     * @throws EnosException
     */
    public InputStream downloadFile(DeviceInfo deviceInfo, String fileUri, FileCategory category)
            throws EnosException, IOException {
        Call call = generateFileDownloadCall(orgId, deviceInfo, fileUri, category, null, null);
        return handleCall(call);
    }

    public void downloadFile(
            DeviceInfo deviceInfo, String fileUri, FileCategory category, IFileCallback callback)
            throws EnosException {
        downloadFile(deviceInfo, fileUri, category, null, null, callback);
    }

    public void downloadFile(
            DeviceInfo deviceInfo,
            String fileUri,
            FileCategory category,
            Long startRange,
            Long endRange,
            IFileCallback callback)
            throws EnosException {
        Call call =
                generateFileDownloadCall(
                        orgId, deviceInfo, fileUri, category, startRange, endRange);

        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            callback.onFailure(
                                    new EnosException(response.code(), response.message()));
                        }

                        try {
                            Preconditions.checkNotNull(response);
                            Preconditions.checkNotNull(response.body());
                            if (response.isSuccessful() && response.body() != null) {
                                callback.onResponse(response.body().byteStream());

                                if (response.code() == 206) {
                                    RangeFileBody.RangeFileBodyBuilder builder =
                                            RangeFileBody.builder();
                                    RangeFileBody rangeFileBody =
                                            builder.contentLength(
                                                            Integer.parseInt(
                                                                    response.headers()
                                                                            .get("Content-length")))
                                                    .contentRange(
                                                            response.headers().get("Content-Range"))
                                                    .acceptRanges(
                                                            response.headers().get("Accept-Ranges"))
                                                    .data(response.body().byteStream())
                                                    .build();
                                    callback.onRangeResponse(rangeFileBody);
                                }
                            }
                        } catch (Exception e) {
                            log.info("failed to get response: " + response, e);
                            callback.onFailure(new EnosException(CLIENT_ERROR));
                        }
                    }
                });
    }

    public RangeFileBody downloadFirmwareFile(String firmwareId, Long startRange, Long endRange)
            throws EnosException, IOException {
        RangeFileBody.RangeFileBodyBuilder builder = RangeFileBody.builder();

        Call call = generateFirmwareFileDownloadCall(orgId, firmwareId, startRange, endRange);
        Response httpResponse;
        try {
            httpResponse = call.execute();

            if (!httpResponse.isSuccessful()) {
                throw new EnosException(httpResponse.code(), httpResponse.message());
            }

            try {
                Preconditions.checkNotNull(httpResponse);
                Preconditions.checkNotNull(httpResponse.body());

                return builder.contentLength(
                                Integer.parseInt(httpResponse.headers().get("Content-length")))
                        .contentRange(httpResponse.headers().get("Content-Range"))
                        .acceptRanges(httpResponse.headers().get("Accept-Ranges"))
                        .data(httpResponse.body().byteStream())
                        .build();
            } catch (Exception e) {
                log.info("failed to get response: " + httpResponse, e);
                throw new EnosException(CLIENT_ERROR);
            }
        } catch (SocketException e) {
            log.info("failed to execute request due to socket error {}", e.getMessage());
            throw new EnosException(SOCKET_ERROR, e.getMessage());
        } catch (EnosException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to execute request", e);
            throw new EnosException(CLIENT_ERROR);
        }
    }

    public InputStream downloadFirmwareFile(String firmwareId)
            throws EnosException, IOException {
        Call call = generateFirmwareFileDownloadCall(orgId, firmwareId, null, null);
        return handleCall(call);
    }

    public void downloadFirmwareFile(String firmwareId, IFileCallback callback)
            throws EnosException {
        downloadFirmwareFile(firmwareId, null, null, callback);
    }

    public void downloadFirmwareFile(
            String firmwareId, Long startRange, Long endRange, IFileCallback callback)
            throws EnosException {
        Call call = generateFirmwareFileDownloadCall(orgId, firmwareId, startRange, endRange);

        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            callback.onFailure(
                                    new EnosException(response.code(), response.message()));
                        }

                        try {
                            Preconditions.checkNotNull(response);
                            Preconditions.checkNotNull(response.body());
                            if (response.isSuccessful() && response.body() != null) {
                                callback.onResponse(response.body().byteStream());

                                if (response.code() == 206) {
                                    RangeFileBody.RangeFileBodyBuilder builder =
                                            RangeFileBody.builder();
                                    RangeFileBody rangeFileBody =
                                            builder.contentLength(
                                                            Integer.parseInt(
                                                                    response.headers()
                                                                            .get("Content-length")))
                                                    .contentRange(
                                                            response.headers().get("Content-Range"))
                                                    .acceptRanges(
                                                            response.headers().get("Accept-Ranges"))
                                                    .data(response.body().byteStream())
                                                    .build();
                                    callback.onRangeResponse(rangeFileBody);
                                }
                            }
                        } catch (Exception e) {
                            log.info("failed to get response: " + response, e);
                            callback.onFailure(new EnosException(CLIENT_ERROR));
                        }
                    }
                });
    }

    public String getDownloadUrl(DeviceInfo deviceInfo, String fileUri, FileCategory category)
            throws EnosException {
        Call call = generateGetDownloadUrlCall(deviceInfo, fileUri, category);

        try {
            Response httpResponse = call.execute();

            Preconditions.checkNotNull(httpResponse);
            Preconditions.checkNotNull(httpResponse.body());

            FileDownloadResponse response =
                    GsonUtil.fromJson(httpResponse.body().string(), FileDownloadResponse.class);
            if (!response.isSuccess()) {
                throw new EnosException(response.getCode(), response.getMsg());
            }
            return response.getData();
        } catch (SocketException e) {
            log.info("failed to execute request due to socket error {}", e.getMessage());
            throw new EnosException(SOCKET_ERROR, e.getMessage());
        } catch (EnosException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to execute request", e);
            throw new EnosException(CLIENT_ERROR);
        }
    }

    /** complete a Request message */
    private void fillRequest(BaseIntegrationRequest request) {
        if (Strings.isNullOrEmpty(request.getId())) {
            request.setId(String.valueOf(seqId.incrementAndGet()));
        }

        // Also populate request version for http
        request.setVersion(VERSION);
    }

    /**
     * Execute okHttp Call, Get Response
     *
     * @param call
     * @return
     * @throws EnosException
     */
    private <T> T publishCall(Call call, Class<T> t) throws EnosException {
        Response httpResponse;
        try {
            httpResponse = call.execute();
            if (!httpResponse.isSuccessful()) {
                throw new EnosException(httpResponse.code(), httpResponse.message());
            }

            try {
                Preconditions.checkNotNull(httpResponse);
                Preconditions.checkNotNull(httpResponse.body());
                byte[] payload = httpResponse.body().bytes();
                String msg = new String(payload, UTF_8);
                return GsonUtil.fromJson(msg, t);
            } catch (Exception e) {
                log.info("failed to decode response: " + httpResponse, e);
                throw new EnosException(CLIENT_ERROR);
            }
        } catch (SocketException e) {
            log.info("failed to execute request due to socket error {}", e.getMessage());
            throw new EnosException(SOCKET_ERROR, e.getMessage());
        } catch (EnosException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to execute request", e);
            throw new EnosException(CLIENT_ERROR);
        }
    }

    private void uploadFileByUrl(BaseIntegrationRequest request, IntegrationResponse response) {
        List<UriInfo> uriInfos = new ArrayList<>();
        if (response.getData() != null) {
            uriInfos = response.getData().getUriInfoList();
        }
        List<UploadFileInfo> fileInfos = request.getFiles();

        Map<String, File> featureIdAndFileMap = new HashMap<>();
        fileInfos.forEach(
                fileInfo -> featureIdAndFileMap.put(fileInfo.getFilename(), fileInfo.getFile()));
        uriInfos.forEach(
                uriInfo -> {
                    try {
                        String filename = uriInfo.getFilename();
                        uriInfo.setFilename(featureIdAndFileMap.get(filename).getName());
                        if (autoUpload) {
                            Response uploadFileRsp =
                                    FileUtil.uploadFile(
                                            uriInfo.getUploadUrl(),
                                            featureIdAndFileMap.get(filename),
                                            uriInfo.getHeaders());
                            if (!uploadFileRsp.isSuccessful()) {
                                log.error(
                                        "Fail to upload file automatically, filename: {}, uploadUrl: {}, msg: {}",
                                        featureIdAndFileMap.get(filename).getName(),
                                        uriInfo.getUploadUrl(),
                                        uploadFileRsp.message());
                            }
                        }
                    } catch (Exception e) {
                        log.error(
                                "Fail to upload file, uri info: {}, exception: {}",
                                featureIdAndFileMap,
                                e);
                    }
                });
    }

    /**
     * Async execute okHttp Call，use Callback method to process Response
     *
     * @param call
     * @param callback
     */
    private void publishCallAsync(Call call, IIntegrationCallback callback) {

        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        if (!response.isSuccessful()) {
                            callback.onFailure(
                                    new EnosException(response.code(), response.message()));
                        }
                        try {
                            Preconditions.checkNotNull(response);
                            Preconditions.checkNotNull(response.body());
                            byte[] payload = response.body().bytes();
                            String msg = new String(payload, UTF_8);
                            callback.onResponse(GsonUtil.fromJson(msg, IntegrationResponse.class));
                        } catch (Exception e) {
                            log.info("failed to decode response: " + response, e);
                            callback.onFailure(new EnosException(CLIENT_ERROR));
                        }
                    }
                });
    }

    // 异步方式没必要，先注释掉
    //
    //    private void publishCallAsync(Call call, IFileInfoCallBack callback) {
    //
    //        call.enqueue(new Callback() {
    //            @Override
    //            public void onFailure(Call call, IOException e) {
    //                callback.onFailure(e);
    //            }
    //
    //            @Override
    //            public void onResponse(Call call, Response response) {
    //                if (!response.isSuccessful()) {
    //                    callback.onFailure(new EnosException(response.code(),
    // response.message()));
    //                }
    //                try {
    //                    Preconditions.checkNotNull(response);
    //                    Preconditions.checkNotNull(response.body());
    //                    byte[] payload = response.body().bytes();
    //                    String msg = new String(payload, UTF_8);
    //                    callback.onResponse(GsonUtil.fromJson(msg, FileInfoResponse.class));
    //                } catch (Exception e) {
    //                    log.info("failed to decode response: " + response, e);
    //                    callback.onFailure(new EnosException(CLIENT_ERROR));
    //                }
    //            }
    //        });
    //    }

    private Call generatePublishCall(
            BaseIntegrationRequest request,
            List<UploadFileInfo> files,
            IProgressListener progressListener)
            throws IOException, EnosException {
        // ensure access token is gotten
        checkAuth();

        // 将请求消息设置完整
        fillRequest(request);

        // 准备一个Multipart请求消息
        MultipartBody.Builder builder =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(ENOS_MESSAGE, new String(request.encode(), UTF_8));

        if (files != null && !useLark) {
            for (UploadFileInfo uploadFile : files) {
                builder.addPart(FileFormData.createFormData(uploadFile));
            }
        }

        RequestBody body;
        if (progressListener == null) {
            body = builder.build();
        } else {
            body = new ProgressRequestWrapper(builder.build(), progressListener);
        }

        Request httpRequest =
                new Request.Builder()
                        .url(
                                integrationBrokerUrl
                                        + INTEGRATION_PATH
                                        + "?action="
                                        + request.getRequestAction()
                                        + "&orgId="
                                        + orgId
                                        + useLarkPart())
                        .addHeader(APIM_ACCESS_TOKEN, tokenConnection.getAccessToken())
                        .post(body)
                        .build();

        log.info("url: " + httpRequest);

        return okHttpClient.newCall(httpRequest);
    }

    private String useLarkPart() {
        return this.isUseLark() ? "&useLark=" + true : "";
    }

    private Call generateDeleteCall(String orgId, DeviceInfo deviceInfo, String fileUri)
            throws EnosException {
        checkAuth();

        StringBuilder uriBuilder =
                new StringBuilder()
                        .append(integrationBrokerUrl)
                        .append(FILES_PATH)
                        .append("?action=")
                        .append(RequestAction.DELETE_ACTION)
                        .append("&orgId=")
                        .append(orgId)
                        .append("&fileUri=")
                        .append(fileUri);
        if (StringUtil.isNotEmpty(deviceInfo.getAssetId())) {
            uriBuilder.append("&assetId=").append(deviceInfo.getAssetId());
        } else {
            uriBuilder
                    .append("&productKey=")
                    .append(deviceInfo.getProductKey())
                    .append("&deviceKey=")
                    .append(deviceInfo.getDeviceKey());
        }

        Request httpRequest =
                new Request.Builder()
                        .url(uriBuilder.toString())
                        .addHeader(APIM_ACCESS_TOKEN, tokenConnection.getAccessToken())
                        .post(RequestBody.create(null, ""))
                        .build();

        return okHttpClient.newCall(httpRequest);
    }

    private Call generateGetFileInfoCall(
            String orgId, FileCategory category, DeviceInfo deviceInfo, String fileUri)
            throws EnosException {
        checkAuth();

        StringBuilder uriBuilder =
                new StringBuilder()
                        .append(integrationBrokerUrl)
                        .append(FILES_PATH)
                        .append("?action=")
                        .append(RequestAction.GET_FILE_INFO_ACTION)
                        .append("&orgId=")
                        .append(orgId)
                        .append("&fileUri=")
                        .append(fileUri)
                        .append("&category=")
                        .append(category.getName());
        if (StringUtil.isNotEmpty(deviceInfo.getAssetId())) {
            uriBuilder.append("&assetId=").append(deviceInfo.getAssetId());
        } else {
            uriBuilder
                    .append("&productKey=")
                    .append(deviceInfo.getProductKey())
                    .append("&deviceKey=")
                    .append(deviceInfo.getDeviceKey());
        }

        Request httpRequest =
                new Request.Builder()
                        .url(uriBuilder.toString())
                        .addHeader(APIM_ACCESS_TOKEN, tokenConnection.getAccessToken())
                        .get()
                        .build();

        return okHttpClient.newCall(httpRequest);
    }

    private Call generateGetDownloadUrlCall(
            DeviceInfo deviceInfo, String fileUri, FileCategory category) throws EnosException {
        checkAuth();

        StringBuilder uriBuilder =
                new StringBuilder()
                        .append(integrationBrokerUrl)
                        .append(FILES_PATH)
                        .append("?action=")
                        .append(RequestAction.GET_DOWNLOAD_URL_ACTION)
                        .append("&orgId=")
                        .append(orgId)
                        .append("&fileUri=")
                        .append(fileUri)
                        .append("&category=")
                        .append(category.getName());
        if (StringUtil.isNotEmpty(deviceInfo.getAssetId())) {
            uriBuilder.append("&assetId=").append(deviceInfo.getAssetId());
        } else {
            uriBuilder
                    .append("&productKey=")
                    .append(deviceInfo.getProductKey())
                    .append("&deviceKey=")
                    .append(deviceInfo.getDeviceKey());
        }

        Request httpRequest =
                new Request.Builder()
                        .url(uriBuilder.toString())
                        .addHeader(APIM_ACCESS_TOKEN, tokenConnection.getAccessToken())
                        .get()
                        .build();

        return okHttpClient.newCall(httpRequest);
    }

    private Call generateFirmwareFileDownloadCall(
            String orgId, String firmwareId, Long startRange, Long endRange)
            throws EnosException {
        checkAuth();

        StringBuilder uriBuilder =
                new StringBuilder()
                        .append(integrationBrokerUrl)
                        .append(FILES_V25_PATH)
                        .append("?action=")
                        .append(RequestAction.DOWNLOAD_FIRMWARE_FILE_ACTION)
                        .append("&orgId=")
                        .append(orgId)
                        .append("&firmwareId=")
                        .append(firmwareId);

        return generateDownloadCall(uriBuilder, startRange, endRange);
    }

    private Call generateFileDownloadCall(
            String orgId,
            DeviceInfo deviceInfo,
            String fileUri,
            FileCategory category,
            Long startRange,
            Long endRange)
            throws EnosException {
        checkAuth();

        StringBuilder uriBuilder =
                new StringBuilder()
                        .append(integrationBrokerUrl)
                        .append(FILES_PATH)
                        .append("?action=")
                        .append(RequestAction.DOWNLOAD_ACTION)
                        .append("&orgId=")
                        .append(orgId)
                        .append("&fileUri=")
                        .append(fileUri)
                        .append("&category=")
                        .append(category.getName());
        if (StringUtil.isNotEmpty(deviceInfo.getAssetId())) {
            uriBuilder.append("&assetId=").append(deviceInfo.getAssetId());
        } else {
            uriBuilder
                    .append("&productKey=")
                    .append(deviceInfo.getProductKey())
                    .append("&deviceKey=")
                    .append(deviceInfo.getDeviceKey());
        }

        return generateDownloadCall(uriBuilder, startRange, endRange);
    }

    private Call generateDownloadCall(StringBuilder uriBuilder, Long startRange, Long endRange) {

        Request.Builder builder = new Request.Builder();

        if (startRange != null || endRange != null) {
            StringBuilder rangeBuilder = new StringBuilder().append("bytes=");
            if (startRange != null) {
                rangeBuilder.append(startRange);
            }
            rangeBuilder.append("-");
            if (endRange != null) {
                rangeBuilder.append(endRange);
            }
            builder.addHeader(RANGE, rangeBuilder.toString());
        }

        Request httpRequest =
                builder.url(uriBuilder.toString())
                        .addHeader(APIM_ACCESS_TOKEN, tokenConnection.getAccessToken())
                        .get()
                        .build();

        return okHttpClient.newCall(httpRequest);
    }

    private InputStream handleCall(@NonNull Call call) throws EnosException, IOException {
        Response httpResponse;
        try {
            httpResponse = call.execute();

            if (!httpResponse.isSuccessful()) {
                throw new EnosException(httpResponse.code(), httpResponse.message());
            }

            try {
                Preconditions.checkNotNull(httpResponse);
                Preconditions.checkNotNull(httpResponse.body());

                return httpResponse.body().byteStream();
            } catch (Exception e) {
                log.info("failed to get response: " + httpResponse, e);
                throw new EnosException(CLIENT_ERROR);
            }
        } catch (SocketException e) {
            log.info("failed to execute request due to socket error {}", e.getMessage());
            throw new EnosException(SOCKET_ERROR, e.getMessage());
        } catch (EnosException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to execute request", e);
            throw new EnosException(CLIENT_ERROR);
        }
    }
}

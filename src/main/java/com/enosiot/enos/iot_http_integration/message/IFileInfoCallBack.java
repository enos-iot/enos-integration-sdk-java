package com.enosiot.enos.iot_http_integration.message;

public interface IFileInfoCallBack {
    /**
     * handle the async response of the integration request
     *
     * @param response
     */
    void onResponse(FileInfoResponse response);

    /**
     * Handle exception we hit while waiting for the response
     *
     * @param failure
     */
    void onFailure(Exception failure);
}

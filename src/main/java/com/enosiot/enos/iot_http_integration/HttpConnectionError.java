package com.enosiot.enos.iot_http_integration;

import com.enosiot.enos.iot_mqtt_sdk.core.exception.IEnosError;
import lombok.Value;

@Value
public class HttpConnectionError implements IEnosError
{
    public static final HttpConnectionError CLIENT_ERROR = new HttpConnectionError(-1, "client execption");
    public static final HttpConnectionError UNSUCCESSFUL_AUTH = new HttpConnectionError(-2, "unable to get authenticated");
    public static final HttpConnectionError SOCKET_ERROR = new HttpConnectionError(-3, "socket error");

    private final int errorCode;
    private final String errorMessage;
}

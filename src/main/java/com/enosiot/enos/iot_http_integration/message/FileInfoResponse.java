package com.enosiot.enos.iot_http_integration.message;

import com.enosiot.enos.iot_http_integration.dto.FileMetaDto;
import lombok.Data;

@Data
public class FileInfoResponse {

    public static final int SUCCESS_CODE = 0;

    private String requestId;
    private int code;
    private String msg;
    private FileMetaDto data;
}

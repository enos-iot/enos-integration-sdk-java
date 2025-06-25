package com.enosiot.enos.iot_http_integration.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileMetaDto implements Serializable {
    private static final long serialVersionUID = -6439370969321235181L;

    private String orgId;
    private String category;
    private String fileUri;
    private String originalFilename;
    private Integer fileSize;
    private String md5;
    private String signMethod;
    private String sign;
    private Long createTime;
}

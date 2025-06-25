import com.enosiot.enos.iot_http_integration.message.IFileCallback;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.iot_mqtt_sdk.core.internals.constants.RangeFileBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: song.xu
 * @Date: 2023/10/27
 */
public class DownloadFirmwareFileSample {
    // EnOS API Gateway URL and HTTP Integration Channel URL, which can be obtained from Environment
    // Information page in EnOS Console
    static final String API_GW_URL = "http://api_gw_url";
    static final String INTEGRATION_CHANNEL_URL = "http://integration_channel_url";

    // EnOS Application AccessKey and SecretKey, which can be obtain in Application Registration
    // page in EnOS Console
    static final String APP_KEY = "appKey";
    static final String APP_SECRET = "appSecret";

    // Firmware credentials, which can be obtained from Firmware Details page in EnOS Console
    static final String ORG_ID = "orgId";

    // Firmware ID you want to download
    static final String FIRMWARE_ID = "firmwareId";

    // start range
    static final Long START_RANGE = 0L;
    // end range
    static final Long END_RANGE = 10000L;

    public static void main(String[] args) throws EnosException {
        // Construct a http connection
        HttpConnection connection =
                new HttpConnection.Builder(
                        INTEGRATION_CHANNEL_URL, API_GW_URL, APP_KEY, APP_SECRET, ORG_ID)
                        .build();

        // firmware id
        String firmwareId = FIRMWARE_ID;

        // download firmware file and return InputStream
        downloadFirmwareFileReturnInputStream(connection, firmwareId);

        // download firmware file and use IFileCallback
        downloadFirmwareFileUseIFileCallback(connection, firmwareId);

        // download firmware file and return RangeFileBody
        //downloadFirmwareFileReturnRangeFileBody(connection, firmwareId);
    }

    private static void downloadFirmwareFileReturnInputStream(
            HttpConnection connection, String firmwareId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            InputStream inputStream = connection.downloadFirmwareFile(firmwareId);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            byte[] data = outputStream.toByteArray();
            System.out.println(new String(data));
        } catch (EnosException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFirmwareFileUseIFileCallback(
            HttpConnection connection, String firmwareId) {
        // Asynchronously call the file download request
        try {
            connection.downloadFirmwareFile(
                    firmwareId,
                    new IFileCallback() {
                        @Override
                        public void onResponse(InputStream inputStream) throws IOException {
                            System.out.println("download file asynchronously");
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, len);
                            }
                            byte[] data = outputStream.toByteArray();
                            System.out.println(new String(data));
                        }

                        @Override
                        public void onFailure(Exception failure) {
                            failure.printStackTrace();
                        }
                    });
        } catch (EnosException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFirmwareFileReturnRangeFileBody(
            HttpConnection connection, String firmwareId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            RangeFileBody rangeFileBody =
                    connection.downloadFirmwareFile(firmwareId, START_RANGE, END_RANGE);
            InputStream inputStream = rangeFileBody.getData();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            byte[] data = outputStream.toByteArray();
            System.out.println(new String(data));
        } catch (EnosException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
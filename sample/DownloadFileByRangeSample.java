import com.enosiot.enos.iot_http_integration.FileCategory;
import com.enosiot.enos.iot_http_integration.HttpConnection;
import com.enosiot.enos.iot_http_integration.message.IFileCallback;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.iot_mqtt_sdk.core.internals.constants.RangeFileBody;
import com.enosiot.enos.sdk.data.DeviceInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author :charlescai
 * @date :2021-05-10
 */
public class DownloadFileByRangeSample {
    // EnOS Token Server URL and HTTP Broker URL, which can be obtained from Environment Information page in EnOS Console
    private static final String TOKEN_SERVER_URL = "http://token_server_url";
    private static final String BROKER_URL = "http://broker_url";

    // EnOS Application AccessKey and SecretKey, which can be obtain in Application Registration page in EnOS Console
    private static final String APP_KEY = "appKey";
    private static final String APP_SECRET = "appSecret";

    // Device credentials, which can be obtained from Device Details page in EnOS Console
    private static final String ORG_ID = "orgId";
    private static final String ASSET_ID = "assetId";
    static final String PRODUCT_KEY = "productKey";
    static final String DEVICE_KEY = "deviceKey";

    public static void main(String[] args) throws EnosException {
        // Construct a http connection
        HttpConnection connection = new HttpConnection.Builder(
                BROKER_URL, TOKEN_SERVER_URL, APP_KEY, APP_SECRET, ORG_ID)
                .build();

        DeviceInfo deviceInfo = new DeviceInfo().setAssetId(ASSET_ID);
        // fileUri is an enos scheme file uri
        String fileUri = "enos-connect://xxx.txt";

        long startRange = 0;
        long endRange = 1023;
        int bufferLength = 1024;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            RangeFileBody rangeFileBody = connection.downloadFile(deviceInfo, fileUri, FileCategory.FEATURE, startRange, endRange);
            InputStream inputStream = rangeFileBody.getData();
            byte[] buffer = new byte[bufferLength];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            System.out.println(rangeFileBody);
            System.out.println(outputStream);
        } catch (EnosException | IOException e) {
            e.printStackTrace();
        }

        // Asynchronously call the file download request
        try {
            connection.downloadFile(deviceInfo, fileUri, FileCategory.FEATURE, startRange, endRange, new IFileCallback() {
                        @Override
                        public void onRangeResponse(RangeFileBody rangeFileBody) throws IOException{
                            System.out.println("download file asynchronously: " + rangeFileBody);
                            InputStream inputStream = rangeFileBody.getData();
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0 ,len);
                            }
                            System.out.println(outputStream.toString());
                        }

                        @Override
                        public void onFailure(Exception failure) {
                            failure.printStackTrace();
                        }
                    }
            );
        } catch (EnosException e) {
            e.printStackTrace();
        }
    }
}


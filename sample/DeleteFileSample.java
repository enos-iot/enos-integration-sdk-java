import com.enosiot.enos.iot_http_integration.HttpConnection;
import com.enosiot.enos.iot_http_integration.message.IIntegrationCallback;
import com.enosiot.enos.iot_http_integration.message.IntegrationResponse;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.sdk.data.DeviceInfo;
import com.google.gson.GsonBuilder;

/**
 * @author :charlescai
 * @date :2020-03-16
 */
public class DeleteFileSample {
    // EnOS API Gateway URL and HTTP Integration Channel URL, which can be obtained from Environment Information page in EnOS Console
    static final String API_GW_URL = "http://api_gw_url";
    static final String INTEGRATION_CHANNEL_URL = "http://integration_channel_url";

    // EnOS Application AccessKey and SecretKey, which can be obtain in Application Registration page in EnOS Console
    static final String APP_KEY = "appKey";
    static final String APP_SECRET = "appSecret";

    // Device credentials, which can be obtained from Device Details page in EnOS Console
    static final String ORG_ID = "orgId";
    static final String ASSET_ID = "assetId";
    static final String PRODUCT_KEY = "productKey";
    static final String DEVICE_KEY = "deviceKey";

    public static void main(String[] args) throws EnosException {
        // Construct a http connection
        HttpConnection connection = new HttpConnection.Builder(
                INTEGRATION_CHANNEL_URL, API_GW_URL, APP_KEY, APP_SECRET, ORG_ID)
                .build();

        // fileUri is an enos scheme file uri
        String fileUri = "enos-connect://xxx.txt";
        DeviceInfo deviceInfo = new DeviceInfo().setAssetId(ASSET_ID);

        try {
            IntegrationResponse response = connection.deleteFile(deviceInfo, fileUri);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
        } catch (EnosException e) {
            e.printStackTrace();
        }

        // Asynchronously call the file delete request
        try {
            connection.deleteFile(deviceInfo, fileUri, new IIntegrationCallback() {
                @Override
                public void onResponse(IntegrationResponse response) {
                    System.out.println("receive response asynchronously");
                    System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
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
}

package com.enosiot.enos.iot_http_integration;

import com.enosiot.enos.iot_http_integration.HttpConnection;
import com.enosiot.enos.iot_http_integration.message.IntegrationMeasurepointPostRequest;
import com.enosiot.enos.iot_http_integration.message.IntegrationResponse;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.sdk.data.DeviceInfo;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

/**
 * @author :charlescai
 * @date :2020-03-16
 */
public class MeasurepointIntegrationSamplePPE {
    // EnOS Token Server URL and HTTP Broker URL, which can be obtained from Environment Information page in EnOS Console
    static final String TOKEN_SERVER_URL = "https://apim-ppe1.enos-iot.com";
    static final String BROKER_URL = "https://iot-http-integration-ppe1.enos-iot.com";

    // EnOS Application AccessKey and SecretKey, which can be obtain in Application Registration page in EnOS Console
    static final String APP_KEY = "008d2a9c-cb45-4c34-922e-68b2a3421deb";
    static final String APP_SECRET = "de2bbb1e-7f98-4fa2-8138-d6ab6f0ad4f8";

    // Device credentials, which can be obtained from Device Details page in EnOS Console
    static final String ORG_ID = "o15517683199241";
    static final String ASSET_ID = "7ndOrYzj";
    static final String PRODUCT_KEY = "bw2hCwQL";
    static final String DEVICE_KEY = "0Vmc5RJoUV";

    private static IntegrationMeasurepointPostRequest buildMeasurepointPostRequest() {
        DeviceInfo deviceInfo1 = new DeviceInfo().setAssetId(ASSET_ID);
        DeviceInfo deviceInfo2 = new DeviceInfo().setKey(PRODUCT_KEY, DEVICE_KEY);

        // Measurepoints are defined in ThingModel
        HashMap<String, Object> hashMap = Maps.newHashMap();
        hashMap.put("Meter.Ua", new Random().nextDouble());
//        File file = new File("sample1.txt");
//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        hashMap.put("FileMeasurePoint1", file);
//        hashMap.put("StringMeasurePoint1", "enos");
        return IntegrationMeasurepointPostRequest.builder()
                .addMeasurepoint(deviceInfo1, System.currentTimeMillis(), hashMap)
                .addMeasurepoint(deviceInfo2, System.currentTimeMillis(), hashMap)
                .build();
    }

    public static void main(String[] args) throws InterruptedException, EnosException {
        // Construct a http connection
        HttpConnection connection = new HttpConnection.Builder(
                BROKER_URL, TOKEN_SERVER_URL, APP_KEY, APP_SECRET, ORG_ID)
                .build();

        SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记


        while (true) {
            IntegrationMeasurepointPostRequest request = buildMeasurepointPostRequest();

            try
            {
                IntegrationResponse response = connection.publish(request, null);
                Date date = new Date();// 获取当前时间
                System.out.println("现在时间：" + sdf.format(date)); // 输出已经格式化的现在时间（24小时制）
                System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
            } catch (EnosException | IOException e)
            {
                e.printStackTrace();
            }
            Thread.sleep(60000*3);
        }
    }
}

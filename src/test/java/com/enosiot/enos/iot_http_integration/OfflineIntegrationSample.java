package com.enosiot.enos.iot_http_integration;

import com.enosiot.enos.iot_http_integration.HttpConnection;
import com.enosiot.enos.iot_http_integration.message.IIntegrationCallback;
import com.enosiot.enos.iot_http_integration.message.IntegrationAttributePostRequest;
import com.enosiot.enos.iot_http_integration.message.IntegrationMeasurepointPostRequest;
import com.enosiot.enos.iot_http_integration.message.IntegrationResponse;
import com.enosiot.enos.iot_mqtt_sdk.core.exception.EnosException;
import com.enosiot.enos.sdk.data.DeviceInfo;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.HashMap;

public class OfflineIntegrationSample {
    // EnOS Token Server URL and HTTP Broker URL, which can be obtained from Environment Information page in EnOS Console

//    //New beta
//    static final String TOKEN_SERVER_URL = "https://ag-beta1.eniot.io";
//    static final String BROKER_URL = "http://iot-http-integration-beta1.eniot.io";

//
//    //New beta （计量测试）
//    static final String APP_KEY = "0b51e6dc-8db2-4c4d-9fb7-8fecf15bd76b";
//    static final String APP_SECRET = "5d80683f-0f12-4766-a71d-d5dda5979c02";


    //cn5
    static final String TOKEN_SERVER_URL = "https://ag-cn5.enos-iot.com";
    static final String BROKER_URL = "https://iot-http-integration-cn5.enos-iot.com";

    //cn5的APP info
    static final String APP_KEY = "0322d680-2100-46f2-a812-d48fe9f89026";
    static final String APP_SECRET = "1bab9dbd-b45e-47b4-98d6-9d38b29a5ab5";


    // Device credentials, which can be obtained from Device Details page in EnOS Console
    static final String ORG_ID = "o15658408901251";
    static final String ASSET_ID = "Gy9LCCPX";
    static final String PRODUCT_KEY = "N5nHb9B2";
    static final String DEVICE_KEY = "n6GaapGFvz";

    private static IntegrationMeasurepointPostRequest buildMeasurepointPostRequest() {
        DeviceInfo deviceInfo1 = new DeviceInfo().setAssetId(ASSET_ID);
        DeviceInfo deviceInfo2 = new DeviceInfo().setKey(PRODUCT_KEY, DEVICE_KEY);

        // Measurepoints are defined in ThingModel
        HashMap<String, Object> hashMap = Maps.newHashMap();
        hashMap.put("point_a", 123);
        hashMap.put("point_aa", 852);
        //qulityMeasurepoints
//        HashMap<String, Object> valueWithQuality = Maps.newHashMap();
//        valueWithQuality.put("value",235);
//        valueWithQuality.put("quality",10);
//        hashMap.put("quality_candice_Meas_comme_int",valueWithQuality);
//
        return IntegrationMeasurepointPostRequest.builder()
                // Non-real-time integration means offline integration
                .realTimeIntegration(false)
                //通过上述 AssetID 来上传测点
                .addMeasurepoint(deviceInfo1, System.currentTimeMillis(), hashMap)
                //通过上述 PK,DK 来上传测点
                .addMeasurepoint(deviceInfo2, System.currentTimeMillis(), hashMap)
                .build();

    }

    private  static IntegrationAttributePostRequest buildAttributePostRequest(){
        DeviceInfo deviceInfo1 = new DeviceInfo().setAssetId(ASSET_ID);
        DeviceInfo deviceInfo2 = new DeviceInfo().setKey(PRODUCT_KEY, DEVICE_KEY);

        // Attributes are defined in ThingModel
        HashMap<String, Object> hashMap = Maps.newHashMap();
        hashMap.put("attribute1",23);
        hashMap.put("attribute1",23);
        return IntegrationAttributePostRequest.builder()
                // Non-real-time integration means offline integration
                .realTimeIntegration(false)
                //通过上述 AssetID 来上传属性
                .addAttribute(deviceInfo1,hashMap)
                //通过上述 PK,DK 来上传属性
                .addAttribute(deviceInfo2,hashMap)
                .build();
    }


    public static void main(String[] args) throws EnosException {
        // Construct a http connection
        HttpConnection connection = new HttpConnection.Builder(
                BROKER_URL, TOKEN_SERVER_URL, APP_KEY, APP_SECRET, ORG_ID)
                .build();

        IntegrationMeasurepointPostRequest request = buildMeasurepointPostRequest();

        try
        {
            IntegrationResponse response = connection.publish(request, null);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
        } catch (EnosException | IOException e)
        {
            e.printStackTrace();
        }

        // Asynchronously call the measurepoint post
        request = buildMeasurepointPostRequest();

        try {
            connection.publish(request, new IIntegrationCallback() {
                        @Override
                        public void onResponse(IntegrationResponse response) {
                            System.out.println("receive response asynchronously");
                            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
                        }

                        @Override
                        public void onFailure(Exception failure) {
                            failure.printStackTrace();
                        }
                    }, null
            );
        } catch (IOException | EnosException e) {
            e.printStackTrace();
        }
    }
}

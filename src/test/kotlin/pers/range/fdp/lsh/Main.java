package pers.range.fdp.lsh;

import com.lsh.lv2.client.api.Client;
import com.lsh.lv2.client.api.DefaultHandleSubData;
import com.lsh.lv2.client.api.HandleSubData;
import com.lsh.lv2.client.api.LV2_MESSAGE_CONSTANT;
import com.lsh.lv2.client.api.data.Lv2Param;
import com.lsh.lv2.client.config.ClientConfig;

public class Main {

    public static void main(String[] args) {
        ClientConfig clientConfig = new ClientConfig("ws://127.0.0.1:2939 3/lv2/data");
                HandleSubData handleSubData = new DefaultHandleSubData();
        Lv2Param lv2Param = new Lv2Param("600000,000001", LV2_MESSAGE_CONSTANT.SUB_ALL);
        String token = "Username-Password-01";
        Client client = new Client(clientConfig, handleSubData, lv2Param, token);
        client.start();

        try {
            Thread.sleep(5 * 60 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.stop();
    }

}

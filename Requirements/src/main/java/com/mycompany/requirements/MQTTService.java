package com.mycompany.requirements;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTService {
    private static MQTTService instance;
    private final MqttClient client;

    private MQTTService() throws MqttException {
        // Broker settings
        String broker = "tcp://mqtt-broker:1883";
        String clientId = "CentralMQTTClient_" + System.currentTimeMillis();
        client = new MqttClient(broker, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true); // Use false if you want session persistence
        options.setAutomaticReconnect(true);
        client.connect(options);
        System.out.println("Central MQTT Service connected to broker: " + broker);
    }

    public static synchronized MQTTService getInstance() throws MqttException {
        if (instance == null) {
            instance = new MQTTService();
        }
        return instance;
    }

    public MqttClient getClient() {
        return client;
    }

    public synchronized void publish(String topic, String payload) throws MqttException {
        if (!client.isConnected()) {
            client.reconnect();
        }
        MqttMessage message = new MqttMessage(payload.getBytes());
        client.publish(topic, message);
        System.out.println("Published message to topic: " + topic);
    }

    /**
     * Use this subscription method when you want to assign a dedicated listener
     * for a particular topic.
     * @param topic
     * @param listener
     * @throws org.eclipse.paho.client.mqttv3.MqttException
     */
    public void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        client.subscribe(topic, listener);
        System.out.println("Subscribed to topic: " + topic);
    }

    public void disconnect() throws MqttException {
        if (client.isConnected()) {
            client.disconnect();
            System.out.println("Central MQTT Service disconnected from broker.");
        }
    }
}

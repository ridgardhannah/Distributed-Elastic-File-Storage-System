package com.mycompany.requirements;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTHelper {
    private final MqttClient client;

    public MQTTHelper(String brokerUrl, String clientId) throws MqttException {
        // Initialize the MQTT client
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);  // Start a clean session
        connOpts.setAutomaticReconnect(true);  // Enable automatic reconnection
        client.connect(connOpts);
        System.out.println("MQTT client connected to broker: " + brokerUrl);

        // Set a callback to handle connection loss and incoming messages
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("Connection to MQTT broker lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                System.out.println("Message arrived on topic: " + topic);
                System.out.println("Message payload: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("Message delivery complete: " + token.getMessageId());
            }
        });
    }

    public synchronized void publish(String topic, String message) throws MqttException {
        // Check if the client is connected before publishing
        if (!client.isConnected()) {
            System.err.println("MQTT client is not connected. Attempting to reconnect...");
            try {
                client.reconnect();  // Attempt to reconnect
            } catch (MqttException e) {
                System.err.println("Reconnection failed: " + e.getMessage());
                throw e;  // Re-throw the exception to handle it in the calling method
            }
        }

        // Publish the message
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        client.publish(topic, mqttMessage);
        System.out.println("Published message to topic: " + topic);
    }

    public void subscribe(String topic, IMqttMessageListener messageListener) throws MqttException {
    client.subscribe(topic, messageListener);
    System.out.println("Subscribed to topic: " + topic);
}

    
    public void disconnect() throws MqttException {
        // Disconnect the client if it is connected
        if (client.isConnected()) {
            client.disconnect();
            System.out.println("MQTT client disconnected.");
        }
    }
}

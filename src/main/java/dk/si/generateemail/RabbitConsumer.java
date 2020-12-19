package dk.si.generateemail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import get.dk.si.route.Root;
import get.dk.si.route.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitConsumer {

    private static String QUEUE_NAME = "email";
    protected Logger logger = LoggerFactory.getLogger(RabbitConsumer.class.getName());
    private Util util = new Util();
    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    public void consume() {
        try {


            // Same as the producer: tries to create a queue, if it wasn't already created
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("188.166.16.16");
            factory.setUsername("mmmrj1");
            factory.setPassword("mmmrj1");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // Register for a queue
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            // Get notified, if a message for this receiver arrives
            DeliverCallback deliverCallback = (consumerTag, delivery) ->
            {

                String message = new String(delivery.getBody(), "UTF-8");
                Root root = convertToRoot(message, util);
                Email email = convertToEmail(root);
                String json = convertToJson( email);
                try {
                    sendToMail(json);
                } catch (TimeoutException e) {
                    logger.error(e.getStackTrace().toString());
                }
            };
            channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> {
            });
        } catch (Exception e) {
            logger.error(e.getStackTrace().toString());
        }
    }

    private String convertToJson(Email email) {
      return gson.toJson(email);
    }

    private void sendToMail(String email) throws IOException, TimeoutException {
        logger.info("Starting connection to rabbitQue");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("188.166.16.16");
        factory.setUsername("mmmrj1");
        factory.setPassword("mmmrj1");
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare("email_send", false, false, false, null);
            channel.basicPublish("", "email_send", null, email.getBytes("UTF-8"));
        }
        logger.info("Sending to topic: email_send");
    }


    private Email convertToEmail(Root root) {
        LinkedHashMap<String, Object> metaData = root.getMetaData();
        LinkedHashMap<String, Object> travelRequest = (LinkedHashMap<String, Object>) metaData.get("travelRequest");
        LinkedHashMap<String, Object> flights = (LinkedHashMap<String, Object>) metaData.get("flights");
        LinkedHashMap<String, Object> hotels = (LinkedHashMap<String, Object>) metaData.get("hotels");
        LinkedHashMap<String, Object> countryData = (LinkedHashMap<String, Object>) metaData.get("countryData");

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" <h1>Travel Data for " + travelRequest.get("customerName") + "</h1>");

        if (metaData.get("flights") != null) {
            stringBuilder.append("<h2>Fights:</h2>");
            stringBuilder.append("<p>From " + travelRequest.get("cityFrom") + " to " + travelRequest.get("cityTo") + "</p>");
            for (Map.Entry<String, Object> entry: flights.entrySet() ) {
            String value = entry.getValue().toString();
            stringBuilder.append("<p>"+value+"</p>");
            }
        }
        if (metaData.get("hotels") != null) {
            stringBuilder.append("<h2>Hotels:</h2>");
            stringBuilder.append("<p>In "+ travelRequest.get("cityTo") +"</p>");
            for (Map.Entry<String, Object> entry: hotels.entrySet() ) {
                String value = entry.getValue().toString();
                stringBuilder.append("<p>"+value+"</p>");
            }
        }
        if(metaData.get("countryData") != null){
            stringBuilder.append("<h2>Info about "+ travelRequest.get("cityTo") +":</h2>");
            stringBuilder.append("<p>"+travelRequest.get("cityTo")+ " is in "+ countryData.get("countryName") +" ("+countryData.get("countryCode")+").</p>");
            stringBuilder.append("<p>The currency of "+ countryData.get("countryName") +" is "+ countryData.get("countryCurrency") + ".</p>");
            stringBuilder.append("<p>The flag of"+countryData.get("countryName")+".</p>");
            stringBuilder.append("<img src=\""+ countryData.get("flagUrl") +"\" alt=\"img\" />");
        }

        Email email = new Email(stringBuilder.toString(),"Travel info", (String) travelRequest.get("customerEmail"));

        return email;
    }

    private Root convertToRoot(String message, Util util) throws JsonProcessingException {
        return util.rootFromJson(message);
    }

}

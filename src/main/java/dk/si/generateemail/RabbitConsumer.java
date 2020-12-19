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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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
                String json = convertToJson(email);
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


        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<h1>Travel Data for " + travelRequest.get("customerName") + "</h1>");
        if (metaData.get("flights") != null) {
            stringBuilder.append("<hr class=\"solid\">");
            stringBuilder.append("<h2>Fights:</h2>");
            stringBuilder.append("<p>From " + travelRequest.get("cityFrom") + " to " + travelRequest.get("cityTo") + "</p>");
            List<LinkedHashMap<String, Object>> flights = (List<LinkedHashMap<String, Object>>) metaData.get("flights");
            flights.stream().forEach(flight -> {
                stringBuilder.append("<h3>" + flight.get(("companyName")) + "</h3>");
                List<LinkedHashMap<String, Object>> prices = (List<LinkedHashMap<String, Object>>) flight.get("list");
                stringBuilder.append("<ul>");
                prices.stream().forEach(price -> {
                    Date date = new Date(Long.parseLong(price.get("time").toString()) * 1000L);
                    SimpleDateFormat jdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                    jdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    String dateString = jdf.format(date);
                    stringBuilder.append("<li> Price: " + price.get("price") + " Time: " + dateString + "</li>");

                });
                stringBuilder.append("</ul>");
            });
        }

        if (metaData.get("rooms") != null) {
            stringBuilder.append("<hr class=\"solid\">");
            stringBuilder.append("<p> Hotel at " + travelRequest.get("cityTo") + "</p>");
            stringBuilder.append("<h2>Hotel rooms</h2>");
            List<LinkedHashMap<String, Object>> hotels = (List<LinkedHashMap<String, Object>>) metaData.get("rooms");
            stringBuilder.append("<table style=\"border:1px solid #dddddd;\" width=\"100%\" cellpadding=\"8\" cellspacing=\"0\">");

            stringBuilder.append("<tr><th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Hotel</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Address</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Rating</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Distance to Center</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Price</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Size</th>" +
                    "<th style=\"border-style:solid; border-width:1px; border-color:#dddddd;\" >Type</th></tr>");

            hotels.stream().forEach(obj -> {
                LinkedHashMap<String, Object> hotel = (LinkedHashMap<String, Object>) obj.get("hotel");
                stringBuilder.append("<tr>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + hotel.get("name") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + hotel.get("address") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + hotel.get("rating") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + hotel.get("distanceToCenter") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + obj.get("price") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + obj.get("maxCapacity") + "</td>");
                stringBuilder.append("<td style=\"border-style:solid; border-width:1px; border-color:#dddddd;\">" + obj.get("roomType") + "</td>");
                stringBuilder.append("</tr>");
            });
            stringBuilder.append("</table>");
        }

        if (metaData.get("countryData") != null) {
            stringBuilder.append("<hr class=\"solid\">");
            LinkedHashMap<String, Object> countryData = (LinkedHashMap<String, Object>) metaData.get("countryData");
            stringBuilder.append("<h2>Info about " + travelRequest.get("cityTo") + ":</h2>");
            stringBuilder.append("<p>" + travelRequest.get("cityTo") + " is in " + countryData.get("countryName") + " (" + countryData.get("countryCode") + ").</p>");
            stringBuilder.append("<p>The currency of " + countryData.get("countryName") + " is " + countryData.get("countryCurrency") + ".</p>");
            stringBuilder.append("<p>The flag of " + countryData.get("countryName") + ".</p>");
            stringBuilder.append("<img src=\"" + countryData.get("flagUrl") + "\" alt=\"img\" />");
        }

        Email email = new Email(stringBuilder.toString(), "Travel info", (String) travelRequest.get("customerEmail"));

        return email;
    }

    private Root convertToRoot(String message, Util util) throws JsonProcessingException {
        return util.rootFromJson(message);
    }

}

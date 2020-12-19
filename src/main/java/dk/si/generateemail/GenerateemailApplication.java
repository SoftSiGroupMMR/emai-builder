package dk.si.generateemail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GenerateemailApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenerateemailApplication.class, args);
        RabbitConsumer rabbitConsumer = new RabbitConsumer();
        rabbitConsumer.consume();
    }

}

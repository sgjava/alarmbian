package com.codeferm.deepstack;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@NoArgsConstructor
public class DeepStackConfiguration {

    @Configuration
    @EnableFeignClients(clients = Client.class)
    public static class DeepStackFeignConfiguration {
    }
}

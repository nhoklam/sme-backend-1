package sme.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        Bạn là AI Co-pilot của hệ thống SME ERP & POS.
                        Hãy trả lời bằng tiếng Việt, ngắn gọn và chính xác.
                        """)
                .build();
    }
}
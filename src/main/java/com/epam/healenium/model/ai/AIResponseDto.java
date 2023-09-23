package com.epam.healenium.model.ai;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AIResponseDto {

    private String response;
    private String conversationId;
    private String messageId;

}

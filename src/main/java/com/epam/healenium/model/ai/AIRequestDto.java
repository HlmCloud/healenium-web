package com.epam.healenium.model.ai;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AIRequestDto {

    @ToString.Exclude
    private String message;
    private String conversationId;
    private String parentMessageId;
}

package com.epam.healenium.model.ai;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class AISelectorDto {

    private String xpath;
    private String css;
    private BigDecimal probability;
}

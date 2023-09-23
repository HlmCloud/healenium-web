package com.epam.healenium.processor;

import com.epam.healenium.model.HealedElement;
import com.epam.healenium.model.SelectorImitatorDto;
import com.epam.healenium.model.HealingResult;
import com.epam.healenium.model.Locator;
import com.epam.healenium.treecomparing.Node;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Imitate css locator for healed webElement processor
 */
@Slf4j(topic = "healenium")
public class ImitateProcessor extends BaseProcessor {

    public ImitateProcessor(BaseProcessor nextProcessor) {
        super(nextProcessor);
    }

    @Override
    public boolean validate() {
        if (context.getHealingResults().isEmpty()) {
            log.warn("New element locators have not been found.\nScore property = {} is bigger than healing's locator score", engine.getScoreCap());
            throw context.getNoSuchElementException();
        }
        return true;
    }

    @Override
    public void execute() {
        if (isAIHealingResult()) {
            return;
        }
        for (HealingResult healingResult : context.getHealingResults()) {
            Node targetNode = healingResult.getTargetNodes().get(0).getValue();
            Double score = healingResult.getTargetNodes().get(0).getScore();
            HealedElement healedElement = healingResult.getHealedElements().get(0);
            SelectorImitatorDto imitatorDto = new SelectorImitatorDto(targetNode, context.getUserLocator());
            List<Locator> imitatedLocators = restClient.imitate(imitatorDto);
            engine.replaceHealedElementLocator(imitatedLocators, score, healedElement);
        }
    }
}

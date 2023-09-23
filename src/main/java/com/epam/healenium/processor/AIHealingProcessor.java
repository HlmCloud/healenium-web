package com.epam.healenium.processor;

import com.epam.healenium.model.HealedElement;
import com.epam.healenium.model.HealingCandidateDto;
import com.epam.healenium.model.HealingResult;
import com.epam.healenium.model.ReferenceElementsDto;
import com.epam.healenium.model.ai.AIRequestDto;
import com.epam.healenium.model.ai.AIResponseDto;
import com.epam.healenium.model.ai.AISelectorDto;
import com.epam.healenium.treecomparing.Node;
import com.epam.healenium.treecomparing.Scored;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

@Slf4j(topic = "healenium")
public class AIHealingProcessor extends BaseProcessor {

    public AIHealingProcessor(BaseProcessor nextProcessor) {
        super(nextProcessor);
    }

    private static final int MAX_TEXT_LENGTH = 9000;

    private final static String TEMPLATE = "Hello,\n" +
            "I am currently writing an auto-test using Selenium WebDriver. The WebDriver successfully finds the element on the reference web page. However, there is an issue when dealing with the target web page, as the DOM tree has changed on the target web page, and it results in a NoSuchElementException using the selector.\n" +
            "I am sending you the selector, along with the reference web page, part of the reference web page and part of the target web page. Could you kindly assist me in finding the most relevant element and appropriate for the selector on the target web page using LCS algorithm and attribute comparisons?\n" +
            "\n" +
            "Output JSON file with no explanation,\n" +
            "Structure: \n" +
            "- \"xpath\": human readable locator in Xpath \n" +
            "- \"css\": CSS locator for webdriver \n" +
            "- \"probability\": probability (in decimal) \n" +
            "\n" +
            "NOTE: the content I want to send you is quite large, so I will break it down into multiple parts to send it. I will use the following rule: [START PART i/N] this is the content of part i out of N in total [END PART i/N]. Once you receive each part, please acknowledge with 'Received part i/N'. When I notify you that 'ALL PARTS SENT,' you can proceed with processing the data and responding to my requests.\n" +
            "\n" +
            "Thanks";

    private final static String FORM_TEMPLATE = "Selector: '%s'\n" +
            "\n" +
            "Web page version 1:\n" +
            "%s" +
            "\n" +
            "Web page version 2:\n" +
            "%s";

    private final static String GIVE_RESULT = "I need the relevant selector now!\n" +
            "I assume that there should be one element on the page matching the selector and the web page doesn't prone to frequent changes. \n" +
            "Output JSON file with no explanation,\n" +
            "Structure: \n" +
            "- \"xpath\": human readable locator in Xpath \n" +
            "- \"css\": CSS locator for webdriver \n" +
            "- \"probability\": probability (in decimal) \n";

    @SneakyThrows
    @Override
    public void execute() {
        ReferenceElementsDto referenceElementsDto = context.getReferenceElementsDto();
        if (referenceElementsDto.getTable() == null || referenceElementsDto.getTableCssSelector() == null) {
            return;
        }
        String cssValue = context.getTableCssSelector().replace("By.cssSelector: ", "");

        String tableHtml = (String) ((JavascriptExecutor) driver).executeScript(String.format(
                "return document.querySelector('%s').outerHTML;", cssValue));
        ArrayList<Node> nodes = new ArrayList<>();
        Node node = engine.parseTree(tableHtml);
        context.setPageContent(tableHtml);
        nodes.add(node);
        String referenceTable = context.getTable();
        AIRequestDto initMessageCall = new AIRequestDto();
        initMessageCall.setMessage(engine.getIntroduction() != null ? engine.getIntroduction() : TEMPLATE);
        AIResponseDto httpResponse = engine.getClient().aiHealing(initMessageCall);
        String responseText = httpResponse.getResponse();
        String conversationId = httpResponse.getConversationId();
        String messageId = httpResponse.getMessageId();
        String format = format(FORM_TEMPLATE, context.getBy(), referenceTable, tableHtml);
        List<String> prompts = splitPrompts(format);
        for (String prompt : prompts) {
            AIRequestDto messageDto = new AIRequestDto();
            messageDto.setMessage(prompt);
            messageDto.setParentMessageId(messageId);
            messageDto.setConversationId(conversationId);
            AIResponseDto messageResponse = engine.getClient().aiHealing(messageDto);
            messageId = messageResponse.getMessageId();
            responseText = messageResponse.getResponse();
        }
        AISelectorDto healedSelectorDto;
        int startJsonIndex = responseText.indexOf("{");
        if (startJsonIndex >= 0) {
            int finishJsonIndex = responseText.indexOf("}");
            ObjectMapper objectMapper = engine.getClient().getObjectMapper();
            healedSelectorDto = objectMapper.readValue(responseText.substring(startJsonIndex, finishJsonIndex + 1), AISelectorDto.class);
        } else {
            healedSelectorDto = getHealedSelectorDto(conversationId, messageId);
        }
        log.warn("AI result: " + healedSelectorDto);
        double probability = healedSelectorDto.getProbability().doubleValue();
        findHealedElementBySelector(probability, By.xpath(healedSelectorDto.getXpath()), nodes);
        findHealedElementBySelector(probability, By.cssSelector(healedSelectorDto.getCss()), nodes);
    }

    private void findHealedElementBySelector(double probability, By by, ArrayList<Node> node) {
        if (context.getHealingResults().isEmpty()) {
            try {
                WebElement element = driver.findElement(by);
                log.warn("By: {}, Element: {}", by, element);
                context.getElementIds().add(((RemoteWebElement) element).getId());
                context.getElements().add(element);
                context.setAiHealingResult(element);
                HealedElement healedElement = new HealedElement();
                healedElement.setScored(new Scored<>(probability, by));
                healedElement.setElement(element);
                HealingResult healingResult = new HealingResult();
                healingResult.setPaths(node);
                healingResult.getHealedElements().add(healedElement);
                HealingCandidateDto healingCandidateDto = new HealingCandidateDto(probability, null, null, null);
                List<HealingCandidateDto> healingCandidateDtos = new ArrayList<>();
                healingCandidateDtos.add(healingCandidateDto);
                healingResult.setAllHealingCandidates(healingCandidateDtos);
                context.getHealingResults().add(healingResult);
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    public static List<String> splitPrompts(String prompts) {
        List<String> promptParts = new ArrayList<>();
        List<String> finalPromptParts = new ArrayList<>();
        int i = 0;

        while (prompts.length() > MAX_TEXT_LENGTH) {
            String part = prompts.substring(0, MAX_TEXT_LENGTH);
            promptParts.add(part);
            prompts = prompts.substring(MAX_TEXT_LENGTH);
            i++;
        }


        if (!prompts.isEmpty()) {
            promptParts.add(prompts);
            i++;
        }

        for (int j = 1; j <= i; j++) {
            String item = promptParts.get(j - 1);
            String start = String.format("[START PART %s/%s]\n ", j, i);
            String finish = String.format("\n[END PART %s/%s]", j, i);
            finalPromptParts.add(start + item + finish);
        }
        String endItem = finalPromptParts.get(i - 1) + "\n 'ALL PARTS SENT'";
        finalPromptParts.add(i - 1, endItem);
        finalPromptParts.remove(i);

        return finalPromptParts;
    }

    @SneakyThrows
    private AISelectorDto getHealedSelectorDto(String conversationId, String messageId) {
        int i = 0;
        String finalResult = engine.getFinalMessage() != null ? engine.getFinalMessage() : GIVE_RESULT;
        while (i <= 2) {
            AIRequestDto finishMessageCall = new AIRequestDto();
            finishMessageCall.setMessage(finalResult);
            finishMessageCall.setParentMessageId(messageId);
            finishMessageCall.setConversationId(conversationId);
            AIResponseDto finishResponse = engine.getClient().aiHealing(finishMessageCall);
            ObjectMapper objectMapper = engine.getClient().getObjectMapper();
            String response = finishResponse.getResponse();
            messageId = finishResponse.getMessageId();
            int startJsonIndex = response.indexOf("{");
            int finishJsonIndex = response.indexOf("}");
            if (startJsonIndex >= 0 && finishJsonIndex >= 0) {
                String replace = response.substring(startJsonIndex, finishJsonIndex + 1);
                AISelectorDto aiSelectorDto = objectMapper.readValue(replace, AISelectorDto.class);
                if (aiSelectorDto.getCss() != null && aiSelectorDto.getXpath() != null
                        && aiSelectorDto.getProbability() != null)  {
                    return aiSelectorDto;
                }
            }
            i++;
        }
        return new AISelectorDto();
    }

}

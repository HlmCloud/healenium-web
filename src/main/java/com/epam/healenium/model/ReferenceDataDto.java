package com.epam.healenium.model;

import com.epam.healenium.treecomparing.Node;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.openqa.selenium.By;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReferenceDataDto {

    @ToString.Exclude
    private List<Node> path;
    @ToString.Exclude
    private String table;
    private Node tableNode;
    private String tableCssSelector;
    private String url;

}

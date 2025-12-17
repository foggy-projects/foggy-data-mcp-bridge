package com.foggyframework.semantic.common;

import lombok.Data;

import java.util.List;
@Data
public class SemanticModule {

    String scope;

    boolean common;

    List<TermItem> terms;


}

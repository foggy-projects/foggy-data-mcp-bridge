package com.foggyframework.dataset.jdbc.model.config;

import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

@Data
public class SemanticProperties {

    List<String> modelList;

    SemanticLevel metadata;

    SemanticLevel internal;


    public void applyGetMetadata(SemanticMetadataRequest request) {

        if (request.getQmModels() == null || request.getQmModels().isEmpty()) {
            request.setQmModels(modelList);
        }
        // 如果没有指定levels，默认使用level=1
        if (metadata != null) {
            metadata.apply(request);
        }

    }

    public void applyDescriptionModelInternal(SemanticMetadataRequest request) {


        // 如果没有指定levels，默认使用level=1
        if (internal != null) {
            internal.apply(request);
        }
    }

    @Data
    public static class SemanticLevel {
        List<Integer> forceLevels;

        List<Integer> defaultLevels;

        public void apply(SemanticMetadataRequest request) {
            if ((request.getLevels() == null || request.getLevels().isEmpty())) {
                request.setLevels(defaultLevels);
            }
            if (forceLevels != null && !forceLevels.isEmpty()) {
                request.setLevels(forceLevels);
            }
        }
    }

}

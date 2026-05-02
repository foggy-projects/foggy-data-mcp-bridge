package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public class ExportMarkdownTest extends EcommerceTestSupport {

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Test
    public void exportFactSalesQueryModel() throws Exception {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList("FactSalesQueryModel"));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "markdown", SemanticRequestContext.empty());
        
        String content = response.getContent();
        
        Files.createDirectories(Paths.get("d:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge-wt-dev-compose/docs/8.3.0.beta"));
        Files.writeString(Paths.get("d:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge-wt-dev-compose/docs/8.3.0.beta/qm_describe.md"), content);
        
        System.out.println("Exported successfully to qm_describe.md");
    }
}

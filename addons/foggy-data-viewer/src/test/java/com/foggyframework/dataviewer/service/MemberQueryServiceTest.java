package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.domain.MemberQueryResponse;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {

    @Mock
    private QueryFacade queryFacade;

    private MemberQueryService service;

    @BeforeEach
    void setUp() {
        service = new MemberQueryService(queryFacade);
    }

    @Test
    @DisplayName("应使用namespace执行成员查询和已选值回填")
    void shouldUseNamespaceForMemberQueryAndSelectedValueLookup() {
        PagingResultImpl mainResult = new PagingResultImpl();
        mainResult.setItems(List.of(Map.of("id", "C001", "caption", "客户1")));
        mainResult.setTotal(1);

        PagingResultImpl selectedResult = new PagingResultImpl();
        selectedResult.setItems(List.of(Map.of("id", "C002", "caption", "客户2")));
        selectedResult.setTotal(1);

        when(queryFacade.queryModelData(any(PagingRequest.class), eq("tms-ai")))
                .thenReturn(mainResult)
                .thenReturn(selectedResult);

        MemberQueryRequest request = new MemberQueryRequest();
        request.setQmModel("orders");
        request.setFieldName("customer");
        request.setSelectedValues(List.of("C002"));

        MemberQueryResponse response = service.query(request, "tms-ai");

        assertEquals("customer$id", response.getSelectionFieldName());
        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getSelectedItems().size());
        verify(queryFacade, times(2)).queryModelData(any(PagingRequest.class), eq("tms-ai"));
    }
}

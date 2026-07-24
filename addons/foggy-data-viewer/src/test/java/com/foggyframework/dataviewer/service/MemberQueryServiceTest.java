package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.domain.MemberQueryResponse;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import com.foggyframework.dataset.model.api.QueryFacadeResult;
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
        QueryFacadeResult mainResult = new QueryFacadeResult(
                1, false, 0, 20, List.of(Map.of("id", "C001", "caption", "客户1")), null);
        QueryFacadeResult selectedResult = new QueryFacadeResult(
                1, false, 0, 1, List.of(Map.of("id", "C002", "caption", "客户2")), null);

        when(queryFacade.query(any(QueryFacadeRequest.class)))
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
        verify(queryFacade, times(2)).query(any(QueryFacadeRequest.class));
    }
}

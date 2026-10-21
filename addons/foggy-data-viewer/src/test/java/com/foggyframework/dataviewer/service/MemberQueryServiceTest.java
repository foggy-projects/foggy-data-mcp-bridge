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

import static org.junit.jupiter.api.Assertions.*;
import com.foggyframework.dataset.model.semantic.member.MemberBindingService;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {

    @Mock
    private QueryFacade queryFacade;

    @Mock private MemberBindingService bindings;
    private MemberQueryService service;

    @BeforeEach
    void setUp() {
        service = new MemberQueryService(queryFacade);
        ReflectionTestUtils.setField(service,"memberBindings",bindings);
    }

    @Test
    @DisplayName("应使用namespace执行成员查询和已选值回填")
    void shouldUseNamespaceForMemberQueryAndSelectedValueLookup() {
        when(bindings.resolve("orders","customer","tms-ai")).thenReturn(new MemberBindingService.Binding(
                "orders#customer","customer$id","customer$caption",false,List.of()));
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

        MemberQueryResponse response = service.query(request, "tms-ai", "Bearer member-test");

        assertEquals("customer$id", response.getSelectionFieldName());
        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getSelectedItems().size());
        var calls=ArgumentCaptor.forClass(QueryFacadeRequest.class);
        verify(queryFacade, times(2)).query(calls.capture());
        for(var call:calls.getAllValues()) {
            assertEquals("tms-ai",call.getNamespace());assertEquals("Bearer member-test",call.getAuthorization());
            assertEquals("orders#customer",call.getQuery().get("queryModel"));
        }
    }

    @Test void refreshUsesCurrentBindingAndDoesNotCacheFailureOrHierarchy() {
        var req=new MemberQueryRequest();req.setQmModel("Music");req.setFieldName("album$artist$id");
        when(queryFacade.query(any(QueryFacadeRequest.class))).thenReturn(new QueryFacadeResult(
                1,false,0,20,List.of(Map.of("id",1,"caption","Artist")),null));
        when(bindings.resolve("Music","album$artist$id","one"))
                .thenReturn(new MemberBindingService.Binding("Music#album$artist","artistKey","artistName",false,List.of()))
                .thenThrow(new IllegalStateException("refresh unavailable"))
                .thenReturn(new MemberBindingService.Binding("Music#album$artist","newKey","newName",true,List.of("childrenOf")));
        var first=service.query(req,"one","Bearer first");assertEquals("artistKey",first.getSelectionFieldName());assertNull(first.getHierarchical());
        assertThrows(IllegalStateException.class,()->service.query(req,"one","Bearer first"));
        var refreshed=service.query(req,"one","Bearer second");assertEquals("newKey",refreshed.getSelectionFieldName());
        assertEquals("newName",refreshed.getDisplayFieldName());assertTrue(refreshed.getHierarchical());assertEquals(List.of("childrenOf"),refreshed.getHierarchyOps());
        var calls=ArgumentCaptor.forClass(QueryFacadeRequest.class);verify(queryFacade,times(2)).query(calls.capture());
        assertEquals("Music#album$artist",calls.getValue().getQuery().get("queryModel"));
        assertEquals("Bearer second",calls.getValue().getAuthorization());
    }
    @Test void missingBindingCapabilityFailsClosedWithoutInventingFields() {
        ReflectionTestUtils.setField(service,"memberBindings",null);
        var req=new MemberQueryRequest();req.setQmModel("Music");req.setFieldName("artistKey");
        assertThrows(IllegalStateException.class,()->service.query(req,"one"));
        org.mockito.Mockito.verifyNoInteractions(queryFacade);
    }
}

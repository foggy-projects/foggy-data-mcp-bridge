//package com.foggyframework.fsscript.client.proxy.converter;
//
//import com.foggyframework.core.ex.RX;
//
//import java.util.List;
//import java.util.Map;
//
////@AllArgsConstructor
//public class MapFsscriptReturnConverter<T extends Map> extends AbstractReturnConverter<T> implements ReturnConverter {
////    Class<T> listResultClazz;
//
//    @Override
//    public Map convertPagingResult(PagingResultImpl pagingResult) {
//        return convertList(pagingResult.getStart(), pagingResult.getLimit(), pagingResult.getItems());
//    }
//
//    @Override
//    public Map convertList(int start, int limit, List items) {
//        if(items.isEmpty()){
//            return null;
//        }
//        if(items.size()>1){
//            throw RX.throwB("查询期望返回空或一条数据，但实际返回的多于一条");
//        }
//        return (Map) items.get(0);
//    }
//
//
//
//}

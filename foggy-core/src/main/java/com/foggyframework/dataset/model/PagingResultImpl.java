package com.foggyframework.dataset.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * R<PagingResultImpl<SvcRechargeResult>>
 *
 * @param <T>
 */
@ApiModel("统一分页对象(分页返回固定封装对象)")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagingResultImpl<T> implements PagingResult<T> {

    @ApiModelProperty("总条数，当查询条件不要求返回总数时，可能返回-1")
    long total;

    @ApiModelProperty("是否还有后续的数据，一般来说，limit=items中的条数时，我们认为hasNext=true")
    boolean hasNext;

    @ApiModelProperty("起始的位置，第一条记录为0")
    int start;

    @ApiModelProperty("查询参数中的limit")
    int limit;

    @ApiModelProperty(value = "结果列表", required = true)
    List<T> items;
    @ApiModelProperty(value = "汇总数据",notes = "应jdbc-model版本，加入汇总数据项，用于返回除了数量之外的其他汇总信息")
    Object totalData;

    public PagingResultImpl(boolean hasNext, int start, int limit, List items) {
        super();
        this.hasNext = hasNext;
        this.start = start;
        this.limit = limit;
        this.items = items;
        this.total = -1;
    }

    public PagingResultImpl(int total, int start, int limit, List items) {
        super();
        this.total = total;
        this.start = start;
        this.limit = limit;
        this.items = items;

        if (total > (start + limit)) {
            hasNext = true;
        }
    }
    public PagingResultImpl(int total, int start, int limit, List items,Object totalData) {
        super();
        this.total = total;
        this.start = start;
        this.limit = limit;
        this.items = items;
        this.totalData  =totalData;
    }

    public static final PagingResultImpl of(List items,int total){
        return new PagingResultImpl(total,0,items.size(),items);
    }
//    public static final PagingResultImpl of(List items,int start,int limit){
//        return new PagingResultImpl(0,start,limit,items);
//    }
//    public static final PagingResultImpl of(List items,int start,int limit,Object totalData){
//        return new PagingResultImpl(0,start,limit,items,totalData);
//    }
    public static final PagingResultImpl of(List items,int start,int limit,Object totalData,int total){
        return new PagingResultImpl(total,start,limit,items,totalData);
    }


    public int getLimit() {
        return limit;
    }

    public int getPageNumber() {
        if (limit == 0) {
            return 1;
        }
        int pn = (start / limit) + 1;
        return pn;
    }

    public int getPageSize() {
        return limit;
    }

    public int getStart() {
        return start;
    }

    public long getTotal() {
        return total;
    }

    public long getTotalPage() {
        if (limit == 0) {
            return 0;
        }
        return (total / limit);
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }


    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public boolean hasNextPage() {
        return total > (start + limit);
    }

    public boolean hasPreviousPage() {
        return start > 0;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public boolean isEmpty() {
        return items == null ? true : items.isEmpty();
    }

    public static <T> PagingResultImpl<T> valueOf(Page<T> page){
        long totalElements = page.getTotalElements();

        PagingResultImpl<T> result = new PagingResultImpl<>();
        result.setStart((int) page.getPageable().getOffset());
        result.setLimit(page.getSize());

        if (totalElements < 1) {
            result.setTotal(0);
            result.setItems(Collections.EMPTY_LIST);
            return result;
        }

        result.setTotal(totalElements);
        result.setItems(page.getContent());

        return result;
    }

    public Object getTotalData() {
        return totalData;
    }

    public void setTotalData(Object totalData) {
        this.totalData = totalData;
    }
}

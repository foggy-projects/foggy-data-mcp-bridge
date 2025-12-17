package com.foggyframework.dataset.client.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.beans.BeanUtils;

@AllArgsConstructor
@Builder
public class PagingRequest<T> {
    @ApiModelProperty("当前页码,第一页为１")
    protected Integer page;

    @ApiModelProperty("每页条数")
    protected Integer pageSize;

    @ApiModelProperty("起始记录数，第一条为0")
    protected Integer start;

    @ApiModelProperty("每页条数，注意，在pageSize和start都传递的情况下，优先使用limit和start")
    protected Integer limit;

    @ApiModelProperty("请求参数")
    protected T param;

    public PagingRequest() {

    }

    public final static <N> PagingRequest<N> buildPagingRequest(N newPram) {
        PagingRequest<N> p = new PagingRequest<>(1, 10, 0, 10, newPram);
        return p;
    }

    public final static <N> PagingRequest<N> buildPagingRequest(N newPram, int limit) {
        PagingRequest<N> p = new PagingRequest<>(1, limit, 0, limit, newPram);
        return p;
    }

    public <N> PagingRequest<N> copy(N newPram) {
        PagingRequest<N> p = new PagingRequest<>(page, pageSize, start, limit, newPram);
        return p;
    }

    public <N> PagingRequest<N> copyProperties(N newPram) {
        BeanUtils.copyProperties(param, newPram);
        PagingRequest<N> p = new PagingRequest<>(page, pageSize, start, limit, newPram);
        return p;
    }

    public Integer getPage() {
        if (page == null) {
            if (start == null) {
                return 1;
            } else {
                return getPageByStart(start);
            }

        }
        return page;
    }

    public static void main(String[] args) {
        System.out.println(Math.ceil(1 / new Double(10)) + 1);
    }

    private Integer getPageByStart(int start) {
        Double pn = Math.ceil(1 / new Double(getPageSize())) + 1;
        return pn.intValue();
    }

    private Integer getStartByPage(int page) {
        int start = (page - 1) * getLimit();
        return start;
    }


    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        if (pageSize == null) {
            return limit == null ? 10 : limit;
        }
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getStart() {
        if (start == null) {
            if (page == null) {
                return 0;
            } else {
                return getStartByPage(page);
            }

        }
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getLimit() {
        if (limit == null || limit == 0) {
            return pageSize == null ? 10 : pageSize;
        }
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public T getParam() {
        return param;
    }

    public void setParam(T param) {
        this.param = param;
    }
}

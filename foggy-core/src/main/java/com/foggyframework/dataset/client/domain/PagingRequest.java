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
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * 解析分页参数用于查询执行：start/limit 优先，其次从 page/pageSize 换算，最后使用默认值
     */
    public void resolveForQuery(int defaultLimit) {
        // start/limit 已显式传入，直接使用
        if (this.start != null && this.limit != null) {
            return;
        }

        // page 显式传入时，从 page/pageSize 换算
        if (this.start == null && this.page != null) {
            int ps = this.pageSize != null ? this.pageSize : defaultLimit;
            this.start = (this.page - 1) * ps;
            if (this.limit == null) {
                this.limit = ps;
            }
            return;
        }

        // 兜底默认值
        if (this.limit == null) {
            this.limit = defaultLimit;
        }
        if (this.start == null) {
            this.start = 0;
        }
    }

    public T getParam() {
        return param;
    }

    public void setParam(T param) {
        this.param = param;
    }
}

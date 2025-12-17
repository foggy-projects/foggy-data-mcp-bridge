package com.foggyframework.dataset.model.support;

import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.TotalCountSetExtractor;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.List;

@AllArgsConstructor
@Data
public class PagingResultSetExtractor<T> implements TotalCountSetExtractor {

    ResultSetExtractor<List<T>> extractor;

    @Override
    public ResultSetExtractor<List<T>> getResultSetExtractor() {
        return extractor;
    }

    @Override
    public PagingResultImpl extractTotal(Object obj, int start, int limit, int total) {
        return new PagingResultImpl(total, start, limit, (List) obj);
    }


}

package com.foggyframework.dataset.model.engine;

import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.impl.mongo.MongoQueryModel;
import com.foggyframework.dataset.model.spi.DbColumn;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoLogicalConditionTest {
    private final MongoQueryModel model = mock(MongoQueryModel.class);
    private final MongoModelQueryEngine engine = new MongoModelQueryEngine(model);
    private final JdbcQuery query = new JdbcQuery();

    private void field(String name) {
        DbColumn column = mock(DbColumn.class);
        when(column.getName()).thenReturn(name);
        when(column.getSqlColumnName()).thenReturn(name);
        when(model.findJdbcColumn(name)).thenReturn(column);
        when(model.findJdbcColumnForCond(name, true)).thenReturn(column);
    }
    private void add(CondRequestDef condition) {
        ReflectionTestUtils.invokeMethod(engine,"buildSlice",model,query,query.getWhere(),condition,"and");
    }
    @Test void preservesScopeAndNestedOrInsteadOfFlatteningItsChildren() {
        field("tenant"); field("orderNo"); field("thirdNo"); field("status");
        add(new CondRequestDef("tenant","=",42));
        add(CondRequestDef.or(List.of(new CondRequestDef("orderNo","like","0012"),
            CondRequestDef.and(List.of(new CondRequestDef("thirdNo","like","0012"),new CondRequestDef("status","=","OPEN"))))));
        Document actual=engine.buildCondition(query.getWhere()).getCriteriaObject();
        Document expected=new Document("$and",List.of(new Document("tenant",42),new Document("$or",List.of(
            new Document("orderNo",java.util.regex.Pattern.compile("0012")),
            new Document("$and",List.of(new Document("thirdNo",java.util.regex.Pattern.compile("0012")),new Document("status","OPEN")))))));
        assertThat(actual.toJson()).isEqualTo(expected.toJson());
        assertThat(query.getWhere().getConds()).hasSize(2);
    }
    @Test void supportsRepeatedFieldsAndNullMembershipWithoutDuplicateBsonKeys() {
        field("source");
        add(new CondRequestDef("source","in",Arrays.asList(null,"")));
        add(new CondRequestDef("source","!=","BYD"));
        assertThat(engine.buildCondition(query.getWhere()).getCriteriaObject()).containsKey("$and");
    }
    @Test void refusesEmptyGroupsUnsupportedNodesAndEmptyMembership() {
        field("source");
        assertThatThrownBy(()->engine.buildCondition(query.getWhere())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->engine.buildCondition(query.new SqlFragmentCond("raw"))).isInstanceOf(IllegalArgumentException.class);
        add(new CondRequestDef("source","in",List.of()));
        assertThatThrownBy(()->engine.buildCondition(query.getWhere())).isInstanceOf(IllegalArgumentException.class);
    }
}

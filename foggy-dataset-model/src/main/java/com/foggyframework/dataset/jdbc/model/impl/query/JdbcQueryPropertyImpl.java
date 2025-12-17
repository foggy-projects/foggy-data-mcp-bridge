package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.JdbcProperty;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JdbcQueryPropertyImpl extends JdbcObjectSupport implements JdbcQueryProperty {

    JdbcProperty jdbcProperty;

    JdbcQueryAccessImpl queryAccess;

    public JdbcQueryPropertyImpl(JdbcProperty jdbcProperty) {
        this.jdbcProperty = jdbcProperty;
    }

    @Override
    public AiObject getAi() {
        return ai == null ? jdbcProperty.getAi() : null;
    }

    @Override
    public String getName() {
        return StringUtils.isEmpty(super.getName())?jdbcProperty.getName() : super.getName();
    }
    @Override
    public String getCaption() {
        return StringUtils.isEmpty(super.getCaption())?jdbcProperty.getCaption() : super.getCaption();
    }

    
}

package com.foggyframework.dataset.db.table.dll;


import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TableGenerator extends DbObjectGenerator<SqlTable> {

    public TableGenerator(SqlTable dbObject, FDialect dialect) {
        super(dbObject, dialect);
    }

    public String generatorCreate() {
        StringBuilder buf = new StringBuilder(
                hasPrimaryKey() ? dialect.getCreateTableString() : dialect.getCreateMultisetTableString()).append(' ')
                .append(getQualifiedName()).append(" (");

        Iterator<SqlColumn> iter = dbObject.getSqlColumns().iterator();
        while (iter.hasNext()) {
            SqlColumn col = iter.next();

            buf.append(col.getQuotedName(dialect)).append(' ');

            buf.append(col.getSqlType(dialect));

            String defaultValue = col.getDefaultValue();
            if (defaultValue != null) {
                buf.append(" default ").append(defaultValue);
            }

            if (col.isNullable()) {
                buf.append(dialect.getNullColumnString());
            } else {
                buf.append(" not null");
            }

            String columnComment = col.getComment();
            if (columnComment != null) {
                buf.append(dialect.getColumnComment(columnComment));
            }

            if (iter.hasNext()) {
                buf.append(", ");
            }

        }
        if (dbObject.getIdColumn() != null) {
            buf.append(", ").append(dbObject.getIdColumn().sqlConstraintString(dialect));
        }

        buf.append(')');

        if (dbObject.getComment() != null) {
            buf.append(dialect.getTableComment(dbObject.getComment()));
        }
        return buf.toString();
    }

    public String generatorDrop() {
        StringBuilder buf = new StringBuilder("drop table ");
        if (dialect.supportsIfExistsBeforeTableName()) {
            buf.append("if exists ");
        }
        buf.append(getQualifiedName()).append(dialect.getCascadeConstraintsString());
        if (dialect.supportsIfExistsAfterTableName()) {
            buf.append(" if exists");
        }
        return buf.toString();
    }

    private Object getQualifiedName() {
        return dbObject.getQuotedName(dialect);
    }

    private boolean hasPrimaryKey() {
        return false;
    }

    public List<String> sqlAlterStrings(SqlTable tableFromDb) {

        StringBuilder root = new StringBuilder("alter table ").append(getQualifiedName()).append("\n");

        Iterator<SqlColumn> iter = dbObject.getSqlColumns().iterator();
        List<String> results = new ArrayList<>();
        StringBuilder alter = root;
        boolean hasAlertColumn = false;
        while (iter.hasNext()) {
            SqlColumn column = iter.next();

            SqlColumn columnFromDb = tableFromDb.getSqlColumn(column.getName(), false);

            if (columnFromDb == null) {
                // the column doesnt exist at all.
                 alter.append(dialect.getAddColumnString()).append(' ').append(column.getQuotedName(dialect))
                        .append(' ').append(column.getSqlType(dialect));

                String defaultValue = column.getDefaultValue();
                if (defaultValue != null) {
                    alter.append(" default ").append(defaultValue);
                }

                if (column.isNullable()) {
                    alter.append(dialect.getNullColumnString());
                } else {
                    alter.append(" not null");
                }

                boolean useUniqueConstraint = column.isUnique() && dialect.supportsUnique()
                        && (!column.isNullable() || dialect.supportsNotNullUnique());
                if (useUniqueConstraint) {
                    alter.append(" unique");
                }

                String columnComment = column.getComment();
                if (columnComment != null) {
                    alter.append(dialect.getColumnComment(columnComment));
                }

                alter.append(",\n");

                hasAlertColumn = true;
            }

        }
        if(hasAlertColumn){
            results.add(root.delete(root.length()-2,root.length()).toString());
        }


        //更新表主键
        if (dbObject.getIdColumn() != null && tableFromDb.getIdColumn() == null) {
            results.add(String.format("alter table %s add primary key(%s);", dbObject.getQuotedName(dialect), dbObject.getIdColumn().getQuotedName(dialect)));
        }


        return results;
    }

}

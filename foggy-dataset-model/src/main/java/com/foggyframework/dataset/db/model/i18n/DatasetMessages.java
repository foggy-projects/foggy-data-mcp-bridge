package com.foggyframework.dataset.db.model.i18n;

import com.foggyframework.dataset.db.model.spi.DbDimension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * Internationalization message utility for Foggy Dataset Model
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
public class DatasetMessages {

    private static final MessageSource messageSource;

    static {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        messageSource = source;
    }

    /**
     * Get message by key with current locale
     *
     * @param key  message key
     * @param args message arguments
     * @return formatted message
     */
    public static String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with specified locale
     *
     * @param key    message key
     * @param locale target locale
     * @param args   message arguments
     * @return formatted message
     */
    public static String getMessage(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    // ==========================================
    // Model Loading Error Messages
    // ==========================================

    public static String modelNotFound(String name) {
        return getMessage("error.model.not.found", name);
    }

    public static String modelTablenameRequired() {
        return getMessage("error.model.tablename.required");
    }

    public static String modelDuplicateDimension(String name) {
        return getMessage("error.model.duplicate.dimension", name);
    }

    public static String modelDuplicateMeasure(String name) {
        return getMessage("error.model.duplicate.measure", name);
    }

    public static String modelDuplicateProperty(String name) {
        return getMessage("error.model.duplicate.property", name);
    }

    public static String modelDuplicateColumn(String name) {
        return getMessage("error.model.duplicate.column", name);
    }

    // ==========================================
    // Query Model Error Messages
    // ==========================================

    public static String querymodelExportMissing(String path) {
        return getMessage("error.querymodel.export.missing", path);
    }

    public static String querymodelModelMissing(String name) {
        return getMessage("error.querymodel.model.missing", name);
    }

    public static String querymodelLoaderRequired(String name) {
        return getMessage("error.querymodel.loader.required", name);
    }

    public static String querymodelAccessInvalid() {
        return getMessage("error.querymodel.access.invalid");
    }

    public static String querymodelColumnNotfound(String qmName, String modelName, String columnName, DbDimension dimension) {
        return getMessage("error.querymodel.column.notfound", qmName, modelName, columnName, dimension == null ? "" : "，该列是维度，请加入$id或$caption");
    }

    public static String querymodelColumnNotfoundSimple(String qmName, String columnName, DbDimension dimension) {
        return getMessage("error.querymodel.column.notfound.simple", qmName, columnName,dimension == null ? "" : "，该列是维度，请加入$id或$caption");
    }

    public static String querymodelQuerycolumnNotfound(String qmName, String columnName) {
        return getMessage("error.querymodel.querycolumn.notfound", qmName, columnName);
    }

    public static String querymodelDimensionNotfound(String modelName, String dimensionName) {
        return getMessage("error.querymodel.dimension.notfound", modelName, dimensionName);
    }

    public static String querymodelPropertyNotfound(String qmName, String propertyName) {
        return getMessage("error.querymodel.property.notfound", qmName, propertyName);
    }

    public static String querymodelDuplicateColumn(String name) {
        return getMessage("error.querymodel.duplicate.column", name);
    }

    public static String querymodelDuplicateQuerycolumn(String name) {
        return getMessage("error.querymodel.duplicate.querycolumn", name);
    }

    // ==========================================
    // Query Engine Error Messages
    // ==========================================

    public static String queryColumnNotfound(String columnName, DbDimension dim) {
        return getMessage("error.query.column.notfound", columnName, dim == null ? "" : "，该列是维度，请加入$id或$caption");
    }

    public static String queryFromDuplicate() {
        return getMessage("error.query.from.duplicate");
    }

    public static String queryColumnAliasDuplicate(String column1, String column2) {
        return getMessage("error.query.column.alias.duplicate", column1, column2);
    }

    public static String queryJoinFieldNotfound(Object queryObject) {
        return getMessage("error.query.join.field.notfound", queryObject);
    }

    public static String queryFieldsRequired() {
        return getMessage("error.query.fields.required");
    }

    public static String queryMixedConditionNotAllowed(String link, String aggregateFields, String normalFields) {
        return getMessage("error.query.mixed.condition.not.allowed", link, aggregateFields, normalFields);
    }

    // ==========================================
    // Formula Error Messages
    // ==========================================

    public static String formulaDuplicate(String name) {
        return getMessage("error.formula.duplicate", name);
    }

    public static String formulaNotfound(String type) {
        return getMessage("error.formula.notfound", type);
    }

    public static String formulaListRequired() {
        return getMessage("error.formula.list.required");
    }

    public static String formulaObjectRequired() {
        return getMessage("error.formula.object.required");
    }

    // ==========================================
    // MongoDB Error Messages
    // ==========================================

    public static String mongoInArrayRequired() {
        return getMessage("error.mongo.in.array.required");
    }

    public static String mongoQuerytypeUnsupported(String type) {
        return getMessage("error.mongo.querytype.unsupported", type);
    }

    // ==========================================
    // Dimension Error Messages
    // ==========================================

    public static String dimensionDictRequired(String name) {
        return getMessage("error.dimension.dict.required", name);
    }

    // ==========================================
    // System Error Messages
    // ==========================================

    public static String systemException() {
        return getMessage("error.system.exception");
    }

    // ==========================================
    // Query Request Validation Error Messages
    // ==========================================

    public static String validationSliceFieldRequired(int index) {
        return getMessage("error.validation.slice.field.required", index + 1);
    }

    public static String validationSliceOpRequired(int index, String field) {
        return getMessage("error.validation.slice.op.required", index + 1, field);
    }

    public static String validationSliceOpInvalid(int index, String field, String op, String supportedOps) {
        return getMessage("error.validation.slice.op.invalid", index + 1, field, op, supportedOps);
    }

    public static String validationSliceValueRequired(int index, String field, String op) {
        return getMessage("error.validation.slice.value.required", index + 1, field, op);
    }

    public static String validationSliceChildrenEmpty(int index, String field) {
        return getMessage("error.validation.slice.children.empty", index + 1, field);
    }

    public static String validationGroupByFieldRequired(int index) {
        return getMessage("error.validation.groupby.field.required", index + 1);
    }

    public static String validationGroupByAggRequired(int index, String field) {
        return getMessage("error.validation.groupby.agg.required", index + 1, field);
    }

    public static String validationGroupByAggInvalid(int index, String field, String agg, String supportedAggs) {
        return getMessage("error.validation.groupby.agg.invalid", index + 1, field, agg, supportedAggs);
    }

    public static String validationOrderByFieldRequired(int index) {
        return getMessage("error.validation.orderby.field.required", index + 1);
    }

    public static String validationOrderByDirRequired(int index, String field) {
        return getMessage("error.validation.orderby.dir.required", index + 1, field);
    }

    public static String validationOrderByDirInvalid(int index, String field, String dir) {
        return getMessage("error.validation.orderby.dir.invalid", index + 1, field, dir);
    }
}

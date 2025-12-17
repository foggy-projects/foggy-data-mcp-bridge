package com.foggyframework.dataset.jdbc.model.semantic.service;

import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维度成员加载器接口
 * 负责加载维度成员数据，提供caption和id之间的映射关系
 */
public interface DimensionMemberLoader {

    /**
     * 加载指定模型和字段的维度成员到内存
     *
     * @param model     模型名称
     * @param fieldName 字段名称
     * @param context   上下文信息，包含扩展数据（如token等）
     * @return 维度成员映射信息
     */
    DimensionMembers loadDimensionMembers(String model, String fieldName, Map<String, Object> context);

    /**
     * 批量加载多个字段的维度成员
     *
     * @param model      模型名称
     * @param fieldNames 字段名称列表
     * @param context    上下文信息，包含扩展数据（如token等）
     * @return 字段名到维度成员的映射
     */
    Map<String, DimensionMembers> loadMultipleDimensions(String model, List<String> fieldNames, Map<String, Object> context);

    /**
     * 根据caption查找对应的id
     *
     * @param members 维度成员数据
     * @param caption caption值
     * @return 对应的id值，如果找不到返回null
     */
    Object findIdByCaption(DimensionMembers members, Object caption);

    /**
     * 根据id查找对应的caption
     *
     * @param members 维度成员数据
     * @param id      id值
     * @return 对应的caption值，如果找不到返回null
     */
    Object findCaptionById(DimensionMembers members, Object id);

    /**
     * 模糊搜索caption，返回匹配的成员列表
     *
     * @param members 维度成员数据
     * @param pattern 搜索模式（支持like语法）
     * @param limit   返回数量限制
     * @return 匹配的成员列表
     */
    List<MemberItem> searchByCaption(DimensionMembers members, String pattern, int limit);

    /**
     * 维度成员数据
     * 由于同一个维度，会在多个模型中使用，如果按模型+维度来缓存，会造成大量的重复性数据，例如网点维度， 1->沆南网点，如果按模型+维度保存，由于维度维度在非常多模型中引用，这会造成大量的内存浪费，
     * 所以我们改为使用 维度的表名为统一缓存
     * model2LoadAt表示某个模型的维度成员被加载的时间，用于判断 是否需要重新加载数据
     */
    @Getter
    @Setter
    class DimensionMembers {
        private String tableName;
        private Map<Object, Object> idToCaptionMap;
        private Map<Object, Object> captionToIdMap;
        private List<MemberItem> allMembers;

        /**
         *
         */
        private final Map<String, Long> model2LoadAt = new ConcurrentHashMap<>();

        public static DimensionMembers of() {
            DimensionMembers members = new DimensionMembers();
            members.setIdToCaptionMap(new HashMap<>());
            members.setCaptionToIdMap(new HashMap<>());
            members.setAllMembers(new ArrayList<>());
            return members;
        }

        public void merge(List<JdbcDataItem> loadDimDataItem) {
            for (JdbcDataItem item : loadDimDataItem) {
                Object id = item.getId();
                Object caption = item.getCaption();
                
                // 添加到双向映射
                if (id != null && caption != null) {
                    idToCaptionMap.put(id, caption);
                    captionToIdMap.put(caption, id);
                    allMembers.add(new MemberItem(id, caption));
                }
            }
        }
    }

    /**
     * 维度成员项
     */
    class MemberItem {
        private Object id;
        private Object caption;
        private Map<String, Object> extra;

        public MemberItem() {
        }

        public MemberItem(Object id, Object caption) {
            this.id = id;
            this.caption = caption;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public Object getCaption() {
            return caption;
        }

        public void setCaption(Object caption) {
            this.caption = caption;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra;
        }
    }
}
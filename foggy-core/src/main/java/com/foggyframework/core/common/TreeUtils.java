package com.foggyframework.core.common;

import java.util.List;

public class TreeUtils {

    /**
     * 前序遍历（Pre-order）
     */
    public static <T> void preOrder(T node, T parent, TreeNodeVisit<T> visit, TreeNodeChildrenProvider<T> childrenProvider) {

        preOrder1(node, parent, 0, 0, visit, childrenProvider);

    }

    /**
     * 前序遍历（Pre-order）
     */
    private static <T> int preOrder1(T node, T parent, int deep, int idx, TreeNodeVisit<T> visit, TreeNodeChildrenProvider<T> childrenProvider) {

        if (node != null) {
            int code = visit.handleTreeNode(deep, idx, node, parent);
            if (code >= TreeNodeCallback.CONTINUE_PARENT)
                return code;
            if (code == TreeNodeCallback.CONTINUE) {
                List<T> children = childrenProvider.getChildren(node);
                if (children == null) {
                    return code;
                }
                int i = 0;
                for (T child : children) {
                    code = preOrder1(child, node, deep + 1, i++, visit, childrenProvider);
                    if (code >= TreeNodeCallback.CONTINUE_PARENT)
                        return code;

                }

            }

        }
        return TreeNodeCallback.CONTINUE;

    }

    /**
     * 后序遍历（Post-order）
     */
    public static <T> void postOrder(T node, T parent, TreeNodeVisit<T> visit, TreeNodeChildrenProvider<T> childrenProvider) {

        postOrder1(node, parent, 0, 0, visit, childrenProvider);

    }

    private static <T> int postOrder1(T node, T parent, int deep, int idx, TreeNodeVisit<T> visit, TreeNodeChildrenProvider<T> childrenProvider) {
        if (node != null) {
            List<T> children = childrenProvider.getChildren(node);
            if (children != null) {
                int i = 0;
                for (T child : children) {
                    int code = postOrder1(child, node, deep + 1, i++, visit, childrenProvider);
                    if (code >= TreeNodeCallback.CONTINUE_PARENT)
                        return code;

                }
            }
            int code = visit.handleTreeNode(deep, idx, node, parent);
            return code;

        }
        return TreeNodeCallback.CONTINUE;
    }


    /**
     * 广度优先遍历（Level order）
     * 未实现
     */


}

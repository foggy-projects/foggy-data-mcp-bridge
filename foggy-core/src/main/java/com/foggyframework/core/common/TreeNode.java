/*
 * ====================================================================
 * This software is subject to the terms of the Common Public License
 * Agreement, available at the following URL:
 *   http://www.opensource.org/licenses/cpl.html .
 * Copyright (C) 2003-2004 TONBELLER AG.
 * All Rights Reserved.
 * You must accept the terms of that agreement to use this software.
 * ====================================================================
 *
 *
 */

package com.foggyframework.core.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tree Node for the for a general tree of Objects
 */
public class TreeNode<T> {

    protected TreeNode<T> parent = null;
    protected List<TreeNode<T>> children = null;
    protected T reference;

    @Override
    public String toString() {
        return "TreeNode{" +
                "reference=" + reference +
                '}';
    }

    /**
     * cTtor
     *
     * @param obj referenced object
     */
    public TreeNode(T obj) {
        this.parent = null;
        this.reference = obj;
        this.children = new ArrayList<TreeNode<T>>();
    }

    public TreeNode(TreeNode<T> parent, T reference) {
        super();
        this.parent = parent;
        this.reference = reference;
        this.children = new ArrayList<TreeNode<T>>();
    }

    /**
     * add child node
     *
     * @param child node to be added
     */
    public void addChildNode(TreeNode child) {
        child.parent = this;
        if (!children.contains(child))
            children.add(child);
    }

    /**
     * deep copy (clone)
     *
     * @return copy of TreeNode
     */
    public TreeNode<T> deepCopy() {
        TreeNode newNode = new TreeNode(reference);
        for (Iterator<TreeNode<T>> iter = children.iterator(); iter.hasNext(); ) {
            TreeNode child = iter.next();
            newNode.addChildNode(child.deepCopy());
        }
        return newNode;
    }

    /**
     * deep copy (clone) and prune
     *
     * @param depth - number of child levels to be copied
     * @return copy of TreeNode
     */
    public TreeNode<T> deepCopyPrune(int depth) {
        if (depth < 0)
            throw new IllegalArgumentException("Depth is negative");
        TreeNode newNode = new TreeNode(reference);
        if (depth == 0)
            return newNode;
        for (Iterator<TreeNode<T>> iter = children.iterator(); iter.hasNext(); ) {
            TreeNode child = iter.next();
            newNode.addChildNode(child.deepCopyPrune(depth - 1));
        }
        return newNode;
    }

    /**
     * @return List of children
     */
    public List<TreeNode<T>> getChildren() {
        return children;
    }

    /**
     * @return level = distance from root
     */
    public int getLevel() {
        int level = 0;
        TreeNode p = parent;
        while (p != null) {
            ++level;
            p = p.parent;
        }
        return level;
    }

    /**
     * @return parent node
     */
    public TreeNode<T> getParent() {
        return parent;
    }

    /**
     * @return reference object
     */
    public T getReference() {
        return reference;
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    /**
     * remove node from tree
     */
    public void remove() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    /**
     * remove child node
     *
     * @param child
     */
    private void removeChild(TreeNode child) {
        children.remove(child);

    }

    /**
     * set reference object
     *
     * @param object reference
     */
    public void setReference(T object) {
        reference = object;
    }

    /**
     * walk through children subtrees of this node
     *
     * @param callbackHandler function called on iteration
     */
    @SuppressWarnings("unused")
    public int walkChildren(TreeNodeCallback<T> callbackHandler) {
        int code = 0;
        int i = 0;
        for (Iterator<TreeNode<T>> iter = children.iterator(); iter.hasNext(); ) {
            TreeNode child = iter.next();
            code = callbackHandler.handleTreeNode(i, child);
            if (code >= TreeNodeCallback.CONTINUE_PARENT)
                return code;
            if (code == TreeNodeCallback.CONTINUE) {
                code = child.walkChildren(callbackHandler);
                if (code > TreeNodeCallback.CONTINUE_PARENT)
                    return code;
            }
            i++;
        }
        return code;
    }

    /**
     * walk through subtree of this node
     *
     * @param callbackHandler function called on iteration
     */
    @SuppressWarnings("unused")
    public int walkTree(TreeNodeCallback<T> callbackHandler) {
        int code = 0;
        code = callbackHandler.handleTreeNode(0,this);
        if (code != TreeNodeCallback.CONTINUE)
            return code;
        for (Iterator<TreeNode<T>> iter = children.iterator(); iter.hasNext(); ) {
            TreeNode child = iter.next();
            code = child.walkTree(callbackHandler);
            if (code >= TreeNodeCallback.CONTINUE_PARENT)
                return code;
        }
        return code;
    }

    /**
     * 从最低层开始
     *
     * @param callbackHandler
     * @return
     */
    public int walkTree1(TreeNodeCallback<T> callbackHandler) {
        int code = 0;

        for (Iterator<TreeNode<T>> iter = children.iterator(); iter.hasNext(); ) {
            TreeNode child = iter.next();
            code = child.walkTree1(callbackHandler);
        }
        code = callbackHandler.handleTreeNode(0,this);

        if (code != TreeNodeCallback.CONTINUE)
            return code;
        return code;
    }

} // TreeNode

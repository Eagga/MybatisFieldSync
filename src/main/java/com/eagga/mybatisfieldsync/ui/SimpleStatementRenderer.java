package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.intellij.ui.SimpleListCellRenderer;

import javax.swing.JList;

/**
 * Statement 下拉框的简洁渲染器。
 */
public class SimpleStatementRenderer extends SimpleListCellRenderer<StatementInfo> {
    @Override
    public void customize(JList<? extends StatementInfo> list,
                          StatementInfo value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
        if (value == null) {
            setText("");
            return;
        }
        setText(value.id() + " (" + value.tagName() + ")");
    }
}

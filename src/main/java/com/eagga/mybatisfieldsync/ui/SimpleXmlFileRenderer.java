package com.eagga.mybatisfieldsync.ui;

import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.SimpleListCellRenderer;

import javax.swing.JList;

/**
 * XML 下拉框渲染器，显示绝对路径以便区分同名文件。
 */
public class SimpleXmlFileRenderer extends SimpleListCellRenderer<XmlFile> {
    @Override
    public void customize(JList<? extends XmlFile> list, XmlFile value, int index, boolean selected, boolean hasFocus) {
        if (value == null || value.getVirtualFile() == null) {
            setText("");
            return;
        }
        setText(value.getVirtualFile().getPath());
    }
}

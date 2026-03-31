package com.eagga.mybatisfieldsync.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
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
        Module module = ModuleUtilCore.findModuleForFile(value.getVirtualFile(), value.getProject());
        String moduleName = module == null ? "no-module" : module.getName();
        setText("[" + moduleName + "] " + value.getVirtualFile().getPath());
    }
}

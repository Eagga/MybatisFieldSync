package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.model.FieldInfo;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段勾选表格模型，使用字段名维护勾选状态。
 */
public class FieldSelectionTableModel extends AbstractTableModel {
    private final String[] columnNames;
    private final List<FieldInfo> fields = new ArrayList<>();
    private final Set<String> selectedFieldNames = new HashSet<>();

    public FieldSelectionTableModel(String selectTitle, String fieldTitle, String typeTitle) {
        this.columnNames = new String[]{selectTitle, fieldTitle, typeTitle};
    }

    public void setFields(List<FieldInfo> newFields) {
        fields.clear();
        fields.addAll(newFields);
        // 刷新字段列表后，仅保留仍存在的勾选项。
        selectedFieldNames.retainAll(fields.stream().map(FieldInfo::name).toList());
        fireTableDataChanged();
    }

    public void selectAll() {
        selectedFieldNames.clear();
        selectedFieldNames.addAll(fields.stream().map(FieldInfo::name).toList());
        fireTableDataChanged();
    }

    public void clearSelection() {
        selectedFieldNames.clear();
        fireTableDataChanged();
    }

    public List<FieldInfo> getSelectedFields() {
        return fields.stream().filter(f -> selectedFieldNames.contains(f.name())).toList();
    }

    public List<FieldInfo> getAllFields() {
        return List.copyOf(fields);
    }

    @Override
    public int getRowCount() {
        return fields.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) {
            return Boolean.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FieldInfo field = fields.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> selectedFieldNames.contains(field.name());
            case 1 -> field.inherited() ? field.name() + " (" + field.ownerClass() + ")" : field.name();
            case 2 -> field.type();
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex != 0) {
            return;
        }

        FieldInfo field = fields.get(rowIndex);
        if (Boolean.TRUE.equals(aValue)) {
            selectedFieldNames.add(field.name());
        } else {
            selectedFieldNames.remove(field.name());
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}

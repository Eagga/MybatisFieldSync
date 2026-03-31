package com.eagga.mybatisfieldsync.ui;

import com.intellij.psi.PsiClass;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量同步实体勾选表格模型。
 */
public class BatchSyncEntityTableModel extends AbstractTableModel {
    private final String[] columnNames;
    private final List<PsiClass> entities = new ArrayList<>();
    private final Set<String> selectedQualifiedNames = new LinkedHashSet<>();

    public BatchSyncEntityTableModel(String selectTitle, String entityTitle) {
        this.columnNames = new String[]{selectTitle, entityTitle};
    }

    public void setEntities(List<PsiClass> newEntities) {
        entities.clear();
        entities.addAll(newEntities);
        selectedQualifiedNames.retainAll(entities.stream()
                .map(PsiClass::getQualifiedName)
                .filter(name -> name != null && !name.isBlank())
                .toList());
        fireTableDataChanged();
    }

    public void setSelectedEntities(List<PsiClass> selectedEntities) {
        selectedQualifiedNames.clear();
        for (PsiClass psiClass : selectedEntities) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null && !qualifiedName.isBlank()) {
                selectedQualifiedNames.add(qualifiedName);
            }
        }
        fireTableDataChanged();
    }

    public void selectAll() {
        selectedQualifiedNames.clear();
        for (PsiClass psiClass : entities) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null && !qualifiedName.isBlank()) {
                selectedQualifiedNames.add(qualifiedName);
            }
        }
        fireTableDataChanged();
    }

    public void clearSelection() {
        selectedQualifiedNames.clear();
        fireTableDataChanged();
    }

    public List<PsiClass> getSelectedEntities() {
        return entities.stream()
                .filter(psiClass -> {
                    String qualifiedName = psiClass.getQualifiedName();
                    return qualifiedName != null && selectedQualifiedNames.contains(qualifiedName);
                })
                .toList();
    }

    @Override
    public int getRowCount() {
        return entities.size();
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
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PsiClass psiClass = entities.get(rowIndex);
        String qualifiedName = psiClass.getQualifiedName();
        return switch (columnIndex) {
            case 0 -> qualifiedName != null && selectedQualifiedNames.contains(qualifiedName);
            case 1 -> qualifiedName == null ? psiClass.getName() : qualifiedName;
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex != 0) {
            return;
        }
        PsiClass psiClass = entities.get(rowIndex);
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return;
        }
        if (Boolean.TRUE.equals(aValue)) {
            selectedQualifiedNames.add(qualifiedName);
        } else {
            selectedQualifiedNames.remove(qualifiedName);
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}

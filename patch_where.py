import re

with open("src/main/java/com/eagga/mybatisfieldsync/service/FieldSyncService.java", "r") as f:
    text = f.read()

# Add syncWhere dispatch
sync_where_dispatch = """        if ("sql".equals(tagName) && id.toLowerCase(Locale.ROOT).contains("column")) {
            syncBaseColumnList(statementTag, selectedFields, allFieldsInOrder);
            return;
        }

        if (id.toLowerCase(Locale.ROOT).contains("where") || !findNestedTagsByName(statementTag, "where").isEmpty()) {
            syncWhere(statementTag, selectedFields, allFieldsInOrder);
            return;
        }"""
text = text.replace("""        if ("sql".equals(tagName) && id.toLowerCase(Locale.ROOT).contains("column")) {
            syncBaseColumnList(statementTag, selectedFields, allFieldsInOrder);
            return;
        }""", sync_where_dispatch)

# Add syncWhere implementation
sync_where_impl = """
    private void syncWhere(@NotNull XmlTag whereTagOwner,
                           @NotNull List<FieldInfo> selectedFields,
                           @NotNull List<FieldInfo> allFieldsInOrder) {
        List<XmlTag> nestedWhere = findNestedTagsByName(whereTagOwner, "where");
        XmlTag targetTag = nestedWhere.isEmpty() ? whereTagOwner : nestedWhere.get(0);

        String indent = detectChildIndent(targetTag);
        String childIndent = indent + IndentUtil.detectIndentUnit(targetTag.getText());

        for (FieldInfo field : selectedFields) {
            if (containsWhereCondition(targetTag.getText(), field)) {
                continue;
            }
            String condition = "and " + NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field);
            XmlTag ifTag = createIfTag(targetTag, buildIfTest(field), condition, childIndent);
            insertIfTagByFieldOrder(targetTag, ifTag, allFieldsInOrder, field, this::containsWhereCondition);
        }
    }

    private boolean containsWhereCondition(@NotNull String body, @NotNull FieldInfo field) {
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_`])`?" + Pattern.quote(NameUtil.camelToSnake(field.name())) + "`?\\s*=").matcher(body).find();
    }
"""

# Insert before syncBaseColumnList
text = text.replace("    private void syncBaseColumnList", sync_where_impl + "\n    private void syncBaseColumnList")

with open("src/main/java/com/eagga/mybatisfieldsync/service/FieldSyncService.java", "w") as f:
    f.write(text)

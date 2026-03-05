import re

with open("src/main/java/com/eagga/mybatisfieldsync/service/FieldSyncService.java", "r") as f:
    text = f.read()

# Add batch logic to syncStatement
target = """        if ("insert".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("insert")) {
            syncInsert(statementTag, selectedFields, allFieldsInOrder);
            return;
        }"""
replacement = """        if ("insert".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("insert")) {
            if (id.toLowerCase(Locale.ROOT).contains("batch") || !findNestedTagsByName(statementTag, "foreach").isEmpty()) {
                syncBatchInsert(statementTag, selectedFields, allFieldsInOrder);
            } else {
                syncInsert(statementTag, selectedFields, allFieldsInOrder);
            }
            return;
        }"""
text = text.replace(target, replacement)

# Add method implementations
methods = """
    private void syncBatchInsert(@NotNull XmlTag insertTag,
                                 @NotNull List<FieldInfo> selectedFields,
                                 @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<XmlTag> foreachTags = findNestedTagsByName(insertTag, "foreach");
        if (foreachTags.isEmpty()) {
            throw new SyncException("Batch insert statement must contain <foreach> tag");
        }
        XmlTag foreachTag = foreachTags.get(0);
        String item = foreachTag.getAttributeValue("item");
        if (item == null || item.isBlank()) {
            item = "item";
        }

        List<XmlTag> trimTags = findNestedTagsByName(insertTag, "trim");
        if (trimTags.size() >= 2) {
            mergeBatchInsertTrim(trimTags.get(0), trimTags.get(1), item, selectedFields, allFieldsInOrder);
            return;
        }

        if (trimTags.size() == 1) {
            List<XmlTag> foreachTrims = findNestedTagsByName(foreachTag, "trim");
            if (!foreachTrims.isEmpty()) {
                mergeBatchInsertTrim(trimTags.get(0), foreachTrims.get(0), item, selectedFields, allFieldsInOrder);
                return;
            }
        }

        List<String> columns = selectedFields.stream().map(field -> NameUtil.camelToSnake(field.name())).toList();
        List<String> values = selectedFields.stream().map(field -> buildBatchParamPlaceholder(field, item)).toList();

        // simple regex replacement
        String insertText = insertTag.getValue().getText();
        String cols = "(" + String.join(", ", columns) + ")";
        String rawColRe = "(?is)(\\\\(\\\\s*[^()]*\\\\))(\\\\s*values)";
        Matcher cm = Pattern.compile(rawColRe).matcher(insertText);
        
        if (cm.find()) {
            insertText = cm.replaceFirst(Matcher.quoteReplacement(cols + cm.group(2)));
        } else {
            // just replace first (...) 
            Matcher cm2 = Pattern.compile("(?is)\\\\(\\\\s*[^()]*\\\\)").matcher(insertText);
            if (cm2.find()) {
                insertText = cm2.replaceFirst(Matcher.quoteReplacement(cols));
            } else {
                throw new SyncException("Cannot find (...) block for columns in batch insert");
            }
        }
        
        insertTag.getValue().setText(insertText);
        
        // foreach part should have been preserved, now replace its values
        foreachTags = findNestedTagsByName(insertTag, "foreach");
        if (!foreachTags.isEmpty()) {
            XmlTag newForeach = foreachTags.get(0);
            String foreachText = newForeach.getValue().getText();
            String vals = "(" + String.join(", ", values) + ")";
            Matcher vm = Pattern.compile("(?is)\\\\(\\\\s*[^()]*\\\\)").matcher(foreachText);
            if (vm.find()) {
                newForeach.getValue().setText(vm.replaceFirst(Matcher.quoteReplacement(vals)));
            } else {
                newForeach.getValue().setText(vals); // or just set it
            }
        }
    }

    private void mergeBatchInsertTrim(@NotNull XmlTag columnTrim,
                                      @NotNull XmlTag valueTrim,
                                      @NotNull String item,
                                      @NotNull List<FieldInfo> selectedFields,
                                      @NotNull List<FieldInfo> allFieldsInOrder) {
        String columnBody = columnTrim.getValue().getText();
        String valueBody = valueTrim.getValue().getText();
        String columnIndent = detectEntryIndent(columnBody);
        String valueIndent = detectEntryIndent(valueBody);

        boolean changed = false;
        for (FieldInfo field : selectedFields) {
            if (!containsColumnEntry(columnBody, field)) {
                columnBody = insertPlainEntryByFieldOrder(
                        columnBody,
                        NameUtil.camelToSnake(field.name()) + ",",
                        columnIndent,
                        allFieldsInOrder,
                        field,
                        this::columnPatternFor
                );
                changed = true;
            }
            if (!containsBatchValueEntry(valueBody, field, item)) {
                valueBody = insertPlainEntryByFieldOrder(
                        valueBody,
                        buildBatchParamPlaceholder(field, item) + ",",
                        valueIndent,
                        allFieldsInOrder,
                        field,
                        f -> batchValuePatternFor(f, item)
                );
                changed = true;
            }
        }

        if (changed) {
            columnTrim.getValue().setText(columnBody);
            valueTrim.getValue().setText(valueBody);
        }
    }

    private String buildBatchParamPlaceholder(@NotNull FieldInfo fieldInfo, @NotNull String item) {
        return "#{" + item + "." + fieldInfo.name() + ",jdbcType=" + fieldInfo.jdbcType() + "}";
    }

    private boolean containsBatchValueEntry(@NotNull String body, @NotNull FieldInfo field, @NotNull String item) {
        return batchValuePatternFor(field, item).matcher(body).find();
    }

    private Pattern batchValuePatternFor(@NotNull FieldInfo field, @NotNull String item) {
        return Pattern.compile("#\\\\{\\\\s*" + Pattern.quote(item + "." + field.name()) + "\\\\b");
    }
"""

text = text.replace("    private void syncUpdate", methods + "\n    private void syncUpdate")

with open("src/main/java/com/eagga/mybatisfieldsync/service/FieldSyncService.java", "w") as f:
    f.write(text)

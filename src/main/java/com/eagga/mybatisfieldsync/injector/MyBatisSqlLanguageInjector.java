package com.eagga.mybatisfieldsync.injector;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyBatisSqlLanguageInjector implements MultiHostInjector {
    private static final List<String> SQL_TAGS = Arrays.asList("insert", "update", "select", "delete", "sql");
    private static final Pattern MYBATIS_PARAM = Pattern.compile("#\\{[^}]*}|\\$\\{[^}]*}");

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlText xmlText)) {
            return;
        }

        String text = xmlText.getText();
        if (text.trim().isEmpty()) {
            return;
        }

        XmlTag parentTag = xmlText.getParentTag();
        if (parentTag == null) {
            return;
        }

        if (isSqlContext(parentTag)) {
            Language sqlLanguage = Language.findLanguageByID("SQL");
            if (sqlLanguage != null) {
                registrar.startInjecting(sqlLanguage);
                injectWithParamReplacement(registrar, (PsiLanguageInjectionHost) context, text);
                registrar.doneInjecting();
            }
        }
    }

    private void injectWithParamReplacement(@NotNull MultiHostRegistrar registrar,
            @NotNull PsiLanguageInjectionHost host, @NotNull String text) {
        Matcher matcher = MYBATIS_PARAM.matcher(text);

        if (!matcher.find()) {
            registrar.addPlace(null, null, host, new TextRange(0, text.length()));
            return;
        }

        matcher.reset();
        int pos = 0;

        while (matcher.find()) {
            if (matcher.start() > pos) {
                registrar.addPlace(null, null, host, new TextRange(pos, matcher.start()));
            }
            registrar.addPlace("?", null, host, TextRange.EMPTY_RANGE);
            pos = matcher.end();
        }

        if (pos < text.length()) {
            registrar.addPlace(null, null, host, new TextRange(pos, text.length()));
        }
    }

    private boolean isSqlContext(XmlTag tag) {
        XmlTag current = tag;
        while (current != null) {
            String name = current.getName().toLowerCase();
            if (SQL_TAGS.contains(name)) {
                XmlTag parent = current.getParentTag();
                return parent != null && "mapper".equals(parent.getName());
            }
            current = current.getParentTag();
        }
        return false;
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(XmlText.class);
    }
}

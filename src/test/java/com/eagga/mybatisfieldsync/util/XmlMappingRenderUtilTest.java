package com.eagga.mybatisfieldsync.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlMappingRenderUtilTest {

    @Test
    void shouldRenderPlaceholderWithJdbcTypeAndTypeHandler() {
        TypeMappingUtil.ResolvedTypeMapping mapping = new TypeMappingUtil.ResolvedTypeMapping(
                "TIMESTAMP",
                "com.demo.LocalDateTimeTypeHandler");

        String placeholder = XmlMappingRenderUtil.buildParameterPlaceholder("createdAt", mapping);

        assertEquals(
                "#{createdAt,jdbcType=TIMESTAMP,typeHandler=com.demo.LocalDateTimeTypeHandler}",
                placeholder);
    }

    @Test
    void shouldRenderResultTagWithCompatibleAttributes() {
        TypeMappingUtil.ResolvedTypeMapping mapping = new TypeMappingUtil.ResolvedTypeMapping(
                "DECIMAL",
                "com.demo.MoneyTypeHandler");

        String tag = XmlMappingRenderUtil.buildResultTag("amount", "amount", mapping);

        assertEquals(
                "<result column=\"amount\" property=\"amount\" jdbcType=\"DECIMAL\" typeHandler=\"com.demo.MoneyTypeHandler\"/>",
                tag);
    }
}

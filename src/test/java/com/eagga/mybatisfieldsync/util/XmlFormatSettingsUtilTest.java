package com.eagga.mybatisfieldsync.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlFormatSettingsUtilTest {

    @Test
    void shouldDetectLeadingCommaMultilineStyleFromExistingBody() {
        String body = """
                  user_id
                , user_name
                , created_at
                """;

        XmlFormatSettingsUtil.ResolvedXmlFormat format = XmlFormatSettingsUtil.resolve(
                body,
                "AUTO",
                "AUTO",
                "AUTO");

        assertEquals("  ", format.indentUnit());
        assertEquals(XmlFormatSettingsUtil.LineBreakStyle.MULTI_LINE, format.lineBreakStyle());
        assertEquals(XmlFormatSettingsUtil.CommaStyle.LEADING, format.commaStyle());
    }

    @Test
    void shouldFallbackToConfiguredDefaultsWhenBodyIsBlank() {
        XmlFormatSettingsUtil.ResolvedXmlFormat format = XmlFormatSettingsUtil.resolve(
                "",
                "TAB",
                "SINGLE_LINE",
                "TRAILING");

        assertEquals("\t", format.indentUnit());
        assertEquals(XmlFormatSettingsUtil.LineBreakStyle.SINGLE_LINE, format.lineBreakStyle());
        assertEquals(XmlFormatSettingsUtil.CommaStyle.TRAILING, format.commaStyle());
    }

    @Test
    void shouldRenderLeadingCommaEntries() {
        XmlFormatSettingsUtil.ResolvedXmlFormat format = new XmlFormatSettingsUtil.ResolvedXmlFormat(
                "    ",
                XmlFormatSettingsUtil.LineBreakStyle.MULTI_LINE,
                XmlFormatSettingsUtil.CommaStyle.LEADING);

        assertEquals("user_id", XmlFormatSettingsUtil.renderEntry("user_id", format, true));
        assertEquals(", user_name", XmlFormatSettingsUtil.renderEntry("user_name", format, false));
    }
}

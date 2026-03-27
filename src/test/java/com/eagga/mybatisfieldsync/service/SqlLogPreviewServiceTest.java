package com.eagga.mybatisfieldsync.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlLogPreviewServiceTest {

    @Test
    void shouldKeepCommaInsideStringParameter() {
        String parameters = "hello,world(String), 7(Integer)";

        List<String> parsed = SqlLogPreviewService.parseParameters(parameters);

        assertEquals(List.of("'hello,world'", "7"), parsed);
        assertEquals("SELECT * FROM user WHERE remark = 'hello,world' AND id = 7",
                SqlLogPreviewService.renderSql(
                        "SELECT * FROM user WHERE remark = ? AND id = ?",
                        parameters));
    }

    @Test
    void shouldEscapeSingleQuoteInStringParameter() {
        assertEquals(List.of("'O''Reilly'"),
                SqlLogPreviewService.parseParameters("O'Reilly(String)"));
    }
}

package com.eagga.mybatisfieldsync.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlFieldSyncSupportTest {

    @Test
    void shouldParseQualifiedAndAliasedBaseColumns() {
        String fragment = "`u`.`user_name`, u.created_at, total_amount AS totalAmount, status status_flag";

        List<XmlFieldSyncSupport.XmlFieldDraft> drafts = XmlFieldSyncSupport.parseBaseColumnList(fragment);

        assertEquals(List.of("userName", "createdAt", "totalAmount", "statusFlag"),
                drafts.stream().map(XmlFieldSyncSupport.XmlFieldDraft::fieldName).toList());
        assertEquals(List.of("user_name", "created_at", "total_amount", "status"),
                drafts.stream().map(XmlFieldSyncSupport.XmlFieldDraft::columnName).toList());
    }

    @Test
    void shouldMatchExistingIdAndResultMappings() {
        String body = """
                <id property="id" column="id" jdbcType="BIGINT"/>
                <result jdbcType="VARCHAR" column="user_name" property="userName"/>
                """;

        assertTrue(XmlFieldSyncSupport.resultMappingPattern("id", "id").matcher(body).find());
        assertTrue(XmlFieldSyncSupport.resultMappingPattern("userName", "user_name").matcher(body).find());

        String withComment = XmlFieldSyncSupport.upsertCommentBeforeLine(body,
                XmlFieldSyncSupport.resultMappingPattern("userName", "user_name"),
                "用户名");
        assertTrue(withComment.contains("<!-- 用户名 -->\n<result jdbcType=\"VARCHAR\" column=\"user_name\" property=\"userName\"/>"));
    }
}

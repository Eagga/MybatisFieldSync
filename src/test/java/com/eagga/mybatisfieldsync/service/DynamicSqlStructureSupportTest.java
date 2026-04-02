package com.eagga.mybatisfieldsync.service;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicSqlStructureSupportTest {

    @Test
    void shouldFindInsertTrimPlansInsideEachChooseBranch() throws Exception {
        DynamicSqlStructureSupport.TagView root = parse("""
                <insert id="insert">
                    INSERT INTO user
                    <choose>
                        <when test="a">
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">name,</if>
                            </trim>
                            VALUES
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">#{name},</if>
                            </trim>
                        </when>
                        <otherwise>
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">name,</if>
                            </trim>
                            VALUES
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">#{name},</if>
                            </trim>
                        </otherwise>
                    </choose>
                </insert>
                """);

        List<DynamicSqlStructureSupport.InsertTrimPlan> plans =
                DynamicSqlStructureSupport.findInsertTrimPlans(root, false);

        assertEquals(2, plans.size());
        assertEquals(List.of(0, 0, 0), plans.get(0).columnPath());
        assertEquals(List.of(0, 0, 1), plans.get(0).valuePath());
        assertEquals(List.of(0, 1, 0), plans.get(1).columnPath());
        assertEquals(List.of(0, 1, 1), plans.get(1).valuePath());
    }

    @Test
    void shouldFindBatchInsertPlansAcrossChooseAndForeach() throws Exception {
        DynamicSqlStructureSupport.TagView root = parse("""
                <insert id="batchInsert">
                    INSERT INTO user
                    <choose>
                        <when test="a">
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">name,</if>
                            </trim>
                            <foreach collection="list" item="item" separator=",">
                                <trim suffixOverrides=",">
                                    <if test="item.name != null">#{item.name},</if>
                                </trim>
                            </foreach>
                        </when>
                        <otherwise>
                            <trim prefix="(" suffix=")" suffixOverrides=",">
                                <if test="name != null">name,</if>
                            </trim>
                            <foreach collection="list" item="row" separator=",">
                                <trim suffixOverrides=",">
                                    <if test="row.name != null">#{row.name},</if>
                                </trim>
                            </foreach>
                        </otherwise>
                    </choose>
                </insert>
                """);

        List<DynamicSqlStructureSupport.InsertTrimPlan> plans =
                DynamicSqlStructureSupport.findInsertTrimPlans(root, true);

        assertEquals(2, plans.size());
        assertEquals("item", plans.get(0).foreachItem());
        assertEquals("row", plans.get(1).foreachItem());
        assertEquals(List.of(0, 0, 0), plans.get(0).columnPath());
        assertEquals(List.of(0, 0, 1, 0), plans.get(0).valuePath());
        assertEquals(List.of(0, 1, 0), plans.get(1).columnPath());
        assertEquals(List.of(0, 1, 1, 0), plans.get(1).valuePath());
    }

    @Test
    void shouldFindUpdateContainersInsideChooseBranches() throws Exception {
        DynamicSqlStructureSupport.TagView root = parse("""
                <set>
                    <choose>
                        <when test="a">
                            <if test="name != null">name = #{name},</if>
                        </when>
                        <otherwise>
                            <if test="name != null">name = #{name},</if>
                        </otherwise>
                    </choose>
                </set>
                """);

        List<List<Integer>> paths = DynamicSqlStructureSupport.findConditionalContainerPaths(root);

        assertEquals(List.of(List.of(0, 0), List.of(0, 1)), paths);
    }

    @Test
    void shouldFindWhereContainersInsideChooseBranches() throws Exception {
        DynamicSqlStructureSupport.TagView root = parse("""
                <where>
                    <choose>
                        <when test="strict">
                            <if test="name != null">and name = #{name}</if>
                        </when>
                        <otherwise>
                            <if test="name != null">and name = #{name}</if>
                        </otherwise>
                    </choose>
                </where>
                """);

        List<List<Integer>> paths = DynamicSqlStructureSupport.findConditionalContainerPaths(root);

        assertEquals(List.of(List.of(0, 0), List.of(0, 1)), paths);
    }

    private DynamicSqlStructureSupport.TagView parse(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return fromElement(document.getDocumentElement());
    }

    private DynamicSqlStructureSupport.TagView fromElement(Element element) {
        List<DynamicSqlStructureSupport.TagView> children = new ArrayList<>();
        Node node = element.getFirstChild();
        while (node != null) {
            if (node instanceof Element childElement) {
                children.add(fromElement(childElement));
            }
            node = node.getNextSibling();
        }
        return new DynamicSqlStructureSupport.TagView() {
            @Override
            public String name() {
                return element.getTagName();
            }

            @Override
            public String text() {
                return element.getTextContent();
            }

            @Override
            public List<? extends DynamicSqlStructureSupport.TagView> children() {
                return children;
            }

            @Override
            public String attribute(String name) {
                return element.hasAttribute(name) ? element.getAttribute(name) : null;
            }
        };
    }
}

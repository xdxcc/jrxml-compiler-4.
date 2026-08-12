package com.example.jasper;

import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsonAndUtilsTest {

    @Test
    public void testJsonRoundTrip() {

        String json =
                "{\"name\":\"报表\",\"count\":3,\"ok\":true,\"list\":[1,2.5,\"x\"],\"n\":null}";

        Map<String, Object> map = Json.parseObject(json);

        assertEquals("报表", map.get("name"));

        assertEquals(3L, map.get("count"));

        assertEquals(Boolean.TRUE, map.get("ok"));

        assertTrue(map.get("list") instanceof List);

        assertEquals(null, map.get("n"));

        // 序列化后能再次解析
        String out = Json.stringify(map);

        Map<String, Object> again = Json.parseObject(out);

        assertEquals("报表", again.get("name"));
    }

    @Test
    public void testJsonEscaping() {

        String json = Json.stringify("a\"b\\c\nd");

        assertEquals("\"a\\\"b\\\\c\\nd\"", json);
    }

    @Test
    public void testExtractParameters() throws Exception {

        File jrxml = new File("sample/simple_test.jrxml");

        if (!jrxml.isFile()) {
            return; // 无示例文件时跳过
        }

        List<JrxmlUtils.ParameterDef> defs =
                JrxmlUtils.extractParameters(jrxml);

        assertNotNull(defs);
    }

    @Test
    public void testConvertValue() {

        assertEquals(
                Integer.valueOf(42),
                JrxmlUtils.convertValue("42", "java.lang.Integer")
        );

        assertEquals(
                Boolean.TRUE,
                JrxmlUtils.convertValue("true", "java.lang.Boolean")
        );

        assertEquals(
                "hello",
                JrxmlUtils.convertValue("hello", "java.lang.String")
        );

        assertEquals(
                null,
                JrxmlUtils.convertValue("  ", "java.lang.String")
        );
    }
}

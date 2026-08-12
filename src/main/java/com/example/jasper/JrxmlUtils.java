package com.example.jasper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JRXML 辅助工具：
 *
 * <ul>
 *   <li>解析报表声明的 {@code <parameter>} 列表</li>
 *   <li>根据参数类型将页面输入的字符串转换为 Java 对象</li>
 * </ul>
 *
 * <p>解析 DTD 时使用空 EntityResolver，避免访问外网。
 */
public final class JrxmlUtils {

    /** 常用日期格式，用于 String -> java.util.Date 转换。 */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "yyyyMMdd"
    };


    private JrxmlUtils() {
    }


    /** JRXML 中声明的单个报表参数。 */
    public static final class ParameterDef {

        public final String name;

        public final String className;

        /** 是否带默认值。 */
        public final boolean hasDefault;

        public final String defaultValue;


        ParameterDef(
                String name,
                String className,
                String defaultValue) {

            this.name = name;

            this.className =
                    className == null
                            ? "java.lang.String"
                            : className;

            this.hasDefault = defaultValue != null;

            this.defaultValue = defaultValue;
        }
    }


    /**
     * 从 JRXML 中提取所有 {@code <parameter>} 定义。
     *
     * <p>DOTALL 兼容：使用 DOM 解析，不依赖 DTD 网络访问。
     */
    public static List<ParameterDef> extractParameters(
            File jrxml) throws Exception {

        List<ParameterDef> result =
                new ArrayList<ParameterDef>();

        Document doc = parseXml(jrxml);

        NodeList list = doc.getElementsByTagName("parameter");

        for (int i = 0; i < list.getLength(); i++) {

            Element el = (Element) list.item(i);

            String name = el.getAttribute("name");

            String className = el.getAttribute("class");

            if (name == null || name.length() == 0) {
                continue;
            }

            String defaultValue = null;

            NodeList children = el.getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {

                Node child = children.item(j);

                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "defaultValueExpression".equals(child.getNodeName())) {

                    defaultValue = child.getTextContent();

                    if (defaultValue != null) {

                        defaultValue = defaultValue.trim();

                        if (defaultValue.length() == 0) {
                            defaultValue = null;
                        }
                    }
                }
            }

            result.add(
                    new ParameterDef(
                            name,
                            className,
                            defaultValue
                    )
            );
        }

        return result;
    }


    /**
     * 将页面输入的字符串按目标类型转换为 Java 对象。
     *
     * <p>空字符串视为 null。
     */
    public static Object convertValue(
            String value,
            String className) {

        if (value == null || value.trim().length() == 0) {
            return null;
        }

        value = value.trim();

        if (className == null) {
            className = "java.lang.String";
        }

        String type = className;

        // 处理 int / boolean 等基本类型
        if ("int".equals(type)) {
            type = "java.lang.Integer";
        } else if ("long".equals(type)) {
            type = "java.lang.Long";
        } else if ("double".equals(type)) {
            type = "java.lang.Double";
        } else if ("float".equals(type)) {
            type = "java.lang.Float";
        } else if ("short".equals(type)) {
            type = "java.lang.Short";
        } else if ("byte".equals(type)) {
            type = "java.lang.Byte";
        } else if ("boolean".equals(type)) {
            type = "java.lang.Boolean";
        } else if ("char".equals(type)) {
            type = "java.lang.Character";
        }

        if ("java.lang.String".equals(type)) {
            return value;
        }

        if ("java.lang.Integer".equals(type)) {
            return Integer.valueOf(value);
        }

        if ("java.lang.Long".equals(type)) {
            return Long.valueOf(value);
        }

        if ("java.lang.Short".equals(type)) {
            return Short.valueOf(value);
        }

        if ("java.lang.Byte".equals(type)) {
            return Byte.valueOf(value);
        }

        if ("java.lang.Double".equals(type)) {
            return Double.valueOf(value);
        }

        if ("java.lang.Float".equals(type)) {
            return Float.valueOf(value);
        }

        if ("java.math.BigDecimal".equals(type)) {
            return new BigDecimal(value);
        }

        if ("java.math.BigInteger".equals(type)) {
            return new BigInteger(value);
        }

        if ("java.lang.Boolean".equals(type)) {
            String v = value.toLowerCase();
            return "true".equals(v)
                    || "yes".equals(v)
                    || "1".equals(v)
                    || "on".equals(v);
        }

        if ("java.lang.Character".equals(type)) {
            if (value.length() != 1) {
                throw new IllegalArgumentException(
                        "Cannot convert '" + value
                                + "' to Character"
                );
            }
            return Character.valueOf(value.charAt(0));
        }

        if ("java.util.Date".equals(type)
                || "java.sql.Date".equals(type)
                || "java.sql.Timestamp".equals(type)) {

            return parseDate(value, type);
        }

        // 未知类型：原样返回字符串，由报表引擎处理
        return value;
    }


    private static Object parseDate(
            String value,
            String type) {

        for (String pattern : DATE_PATTERNS) {

            SimpleDateFormat sdf =
                    new SimpleDateFormat(pattern);

            sdf.setLenient(false);

            try {

                Date d = sdf.parse(value);

                if ("java.sql.Date".equals(type)) {
                    return new java.sql.Date(d.getTime());
                }

                if ("java.sql.Timestamp".equals(type)) {
                    return new java.sql.Timestamp(d.getTime());
                }

                return d;

            } catch (Exception ignore) {
                // 尝试下一种格式
            }
        }

        throw new IllegalArgumentException(
                "Cannot parse date: " + value
        );
    }


    /**
     * DOM 解析 XML。
     *
     * <p>关闭 DTD 校验并使用空 EntityResolver，
     * 防止解析器访问外部 DTD 导致联网/慢。
     */
    private static Document parseXml(
            File file) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(false);

        factory.setValidating(false);

        factory.setExpandEntityReferences(false);

        // 禁用可能触发外部实体加载的特性
        safeSetFeature(
                factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );

        safeSetFeature(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        safeSetFeature(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        DocumentBuilder builder = factory.newDocumentBuilder();

        builder.setEntityResolver(
                new EntityResolver() {
                    public InputSource resolveEntity(
                            String publicId,
                            String systemId) {

                        // 返回空输入，跳过 DTD 外部引用
                        return new InputSource(
                                new StringReader("")
                        );
                    }
                }
        );

        return builder.parse(file);
    }


    private static void safeSetFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value) {

        try {

            factory.setFeature(feature, value);

        } catch (Exception ignore) {
            // 某些实现不支持该特性，忽略
        }
    }
}

package com.fasterxml.jackson.databind.contextual;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static com.fasterxml.jackson.databind.testutil.DatabindTestUtil.*;

public class TestContextAttributeWithDeser
{
    final static String KEY = "foobar";

    @SuppressWarnings("serial")
    static class PrefixStringDeserializer extends StdScalarDeserializer<String>
    {
        protected PrefixStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException
        {
            Integer I = (Integer) ctxt.getAttribute(KEY);
            if (I == null) {
                I = Integer.valueOf(0);
            }
            int i = I.intValue();
            ctxt.setAttribute(KEY, Integer.valueOf(i + 1));
            return jp.getText()+"/"+i;
        }

    }

    static class TestPOJO
    {
        @JsonDeserialize(using=PrefixStringDeserializer.class)
        public String value;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testSimplePerCall() throws Exception
    {
        final String INPUT = a2q("[{'value':'a'},{'value':'b'}]");
        TestPOJO[] pojos = MAPPER.readerFor(TestPOJO[].class).readValue(INPUT);
        assertEquals(2, pojos.length);
        assertEquals("a/0", pojos[0].value);
        assertEquals("b/1", pojos[1].value);

        // and verify that state does not linger
        TestPOJO[] pojos2 = MAPPER.readerFor(TestPOJO[].class).readValue(INPUT);
        assertEquals(2, pojos2.length);
        assertEquals("a/0", pojos2[0].value);
        assertEquals("b/1", pojos2[1].value);
    }

    @Test
    public void testSimpleDefaults() throws Exception
    {
        final String INPUT = a2q("{'value':'x'}");
        TestPOJO pojo = MAPPER.readerFor(TestPOJO.class)
                .withAttribute(KEY, Integer.valueOf(3))
                .readValue(INPUT);
        assertEquals("x/3", pojo.value);

        // as above, should not carry on state
        TestPOJO pojo2 = MAPPER.readerFor(TestPOJO.class)
                .withAttribute(KEY, Integer.valueOf(5))
                .readValue(INPUT);
        assertEquals("x/5", pojo2.value);
    }

    @Test
    public void testHierarchic() throws Exception
    {
        final String INPUT = a2q("[{'value':'x'},{'value':'y'}]");
        ObjectReader r = MAPPER.readerFor(TestPOJO[].class).withAttribute(KEY, Integer.valueOf(2));
        TestPOJO[] pojos = r.readValue(INPUT);
        assertEquals(2, pojos.length);
        assertEquals("x/2", pojos[0].value);
        assertEquals("y/3", pojos[1].value);

        // and once more to verify transiency of per-call state
        TestPOJO[] pojos2 = r.readValue(INPUT);
        assertEquals(2, pojos2.length);
        assertEquals("x/2", pojos2[0].value);
        assertEquals("y/3", pojos2[1].value);
    }

    @Test
    // [databind#3001]
    public void testDefaultsViaMapper() throws Exception
    {
        final String INPUT = a2q("{'value':'x'}");
        ContextAttributes attrs = ContextAttributes.getEmpty()
                .withSharedAttribute(KEY, Integer.valueOf(72));
        ObjectMapper mapper = jsonMapperBuilder()
                .defaultAttributes(attrs)
                .build();
        TestPOJO pojo = mapper.readerFor(TestPOJO.class)
                .readValue(INPUT);
        assertEquals("x/72", pojo.value);

        // as above, should not carry on state
        TestPOJO pojo2 = mapper.readerFor(TestPOJO.class)
                .readValue(INPUT);
        assertEquals("x/72", pojo2.value);

        // And should be overridable too
        TestPOJO pojo3 = mapper.readerFor(TestPOJO.class)
                .withAttribute(KEY, Integer.valueOf(19))
                .readValue(INPUT);
        assertEquals("x/19", pojo3.value);
    }
}

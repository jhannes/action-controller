package org.actioncontroller.optional.json;

import org.junit.Test;

import javax.json.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonValueObjectMappingTest {

    private final Random random = new Random();

    public static class AllTypes {
        public boolean aBool;
        public byte aByte;
        public short aShort;
        public int anInt;
        public long aLong;
        public float aFloat;
        public double aDouble;
        public char aChar;

        public Boolean aBoolWrapper;
        public Byte aByteWrapper;
        public Short aShortWrapper;
        public Integer anIntWrapper;
        public Long aLongWrapper;
        public Float aFloatWrapper;
        public Double aDoubleWrapper;
        public Character aCharWrapper;

        public BigInteger aBigInteger;
        public BigDecimal aBigDecimal;

        public OffsetDateTime offsetDateTime;
        public ZonedDateTime zonedDateTime;
        public LocalDate localDate;
        public Instant instant;

        public UUID uuid;
        public URL url;
        public URI uri;
        public InetAddress inetAddress;
    }

    @Test
    public void shouldMapPrimitives() {
        AllTypes o = new AllTypes();
        o.aBool = random.nextBoolean();
        o.aChar = (char)('a' + (random.nextInt() % 26));
        o.aByte = (byte) (random.nextInt() & 0xff);
        o.aShort = (short) (random.nextInt() & 0xffff);
        o.anInt = random.nextInt();
        o.aLong = random.nextLong();
        o.aFloat = random.nextFloat();
        o.aDouble = random.nextDouble();

        JsonValue json = new JsonGenerator().toJson(o);
        assertThat(new PojoMapper().map(json, AllTypes.class))
                .usingRecursiveComparison()
                .isEqualTo(o);
    }

    @Test
    public void shouldMapWrappers() {
        AllTypes o = new AllTypes();
        o.aBoolWrapper = random.nextBoolean();
        o.aCharWrapper = (char)('a' + (random.nextInt() % 26));
        o.aByteWrapper = (byte) (random.nextInt() & 0xff);
        o.aShortWrapper = (short) (random.nextInt() & 0xffff);
        o.anIntWrapper = random.nextInt();
        o.aLongWrapper = random.nextLong();
        o.aFloatWrapper = random.nextFloat();
        o.aDoubleWrapper = random.nextDouble();

        JsonValue json = new JsonGenerator().toJson(o);
        assertThat(new PojoMapper().map(json, AllTypes.class))
                .usingRecursiveComparison()
                .isEqualTo(o);
    }

    @Test
    public void shouldMapBigNumeric() {
        AllTypes o = new AllTypes();
        byte[] bytes = new byte[random.nextInt(15) + 1];
        random.nextBytes(bytes);
        o.aBigInteger = new BigInteger(bytes);
        o.aBigDecimal = new BigDecimal(new BigInteger(bytes), random.nextInt(6));

        JsonValue json = new JsonGenerator().toJson(o);
        assertThat(new PojoMapper().map(json, AllTypes.class))
                .usingRecursiveComparison()
                .isEqualTo(o);
    }

    @Test
    public void shouldMapDates() {
        AllTypes o = new AllTypes();
        o.localDate = LocalDate.now().plusDays(random.nextInt(365));
        o.zonedDateTime = ZonedDateTime.now();
        o.offsetDateTime = OffsetDateTime.now();
        o.instant = Instant.now();

        JsonValue json = new JsonGenerator().toJson(o);
        assertThat(new PojoMapper().map(json, AllTypes.class))
                .usingRecursiveComparison()
                .isEqualTo(o);
    }

    @Test
    public void shouldMapStringTypes() throws MalformedURLException, URISyntaxException, UnknownHostException {
        AllTypes o = new AllTypes();
        o.uuid = UUID.randomUUID();
        o.url = new URL("https://example.com");
        o.uri = new URL("https://example.com").toURI();
        o.inetAddress = InetAddress.getByName("example.com");

        JsonValue json = new JsonGenerator().toJson(o);
        assertThat(new PojoMapper().map(json, AllTypes.class))
                .usingRecursiveComparison()
                .isEqualTo(o);
    }


}

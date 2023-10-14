package org.actioncontroller.multipart;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.actioncontroller.actions.POST;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.optional.json.JsonGenerator;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.HttpHeader;
import org.actioncontroller.values.json.JsonBody;
import org.jsonbuddy.JsonNode;
import org.synchronoss.cloud.nio.multipart.MultipartContext;

public class MultipartExampleController {

    public MultipartExampleController() {

    }

    public static final String MULTIPART_POST_PATH = "/multipart/post";

    public static final JsonGenerator jsonGenerator = new JsonGenerator();

    @POST(MULTIPART_POST_PATH)
    @JsonBody
    public JsonNode handleMultipart(
        @ContentBody(contentType = "multipart/form-data") byte[] body,
        @HttpHeader("Content-type") String contentTypeForRequest,
        @HttpHeader("Content-length") Integer contentLength
    ) {
        try {
            /* Supposed to be javax.servlet.request.getContentEncoding() - but not available .. */
            String contentEncoding = StandardCharsets.UTF_8.name();
            var context = new MultipartContext(contentTypeForRequest, contentLength, contentEncoding);

            Map<String, Object> parse = new MultipartParser(context, new ByteArrayInputStream(body))
                .parse();

            return org.jsonbuddy.pojo.JsonGenerator.generate(parse);
        } catch(Exception e) {
            throw new HttpActionException(500, e);
        }

    }
}

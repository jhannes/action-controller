package org.actioncontroller.optional.multipart;

import org.actioncontroller.actions.POST;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.HttpHeader;
import org.actioncontroller.values.json.JsonBody;
import org.jsonbuddy.JsonNode;
import org.synchronoss.cloud.nio.multipart.MultipartContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MultipartExampleController {

    @POST("/multipart/post")
    @JsonBody
    public JsonNode handleMultipart(
            @ContentBody(contentType = "multipart/form-data") byte[] body,
            @HttpHeader("Content-Type") String contentTypeForRequest,
            @HttpHeader("Content-Length") Integer contentLength
    ) throws IOException {
        /* TODO: Content Encoding is available as @Header("Content-Encoding") or ApiHttpExchange.getHeaders("Content-Encoding"). Or as part of Content-Type  */

        String contentEncoding = StandardCharsets.UTF_8.name();
        MultipartContext context = new MultipartContext(contentTypeForRequest, contentLength, contentEncoding);

        Map<String, Object> parse = new MultipartParser(context, new ByteArrayInputStream(body))
                .parse();

        return org.jsonbuddy.pojo.JsonGenerator.generate(parse);
    }
}

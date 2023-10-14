package org.actioncontroller.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsonbuddy.JsonObject;
import org.synchronoss.cloud.nio.multipart.BlockingIOAdapter;
import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.MultipartUtils;
import org.synchronoss.cloud.nio.multipart.util.IOUtils;
import org.synchronoss.cloud.nio.multipart.util.collect.CloseableIterator;

public class MultipartParser {
    private final Map<String, Object> parsedRequest = new HashMap<>();
    private final CloseableIterator<BlockingIOAdapter.ParserToken> parts;
    private final MultipartContext context;

    public MultipartParser(MultipartContext context, InputStream body) {
        this.context = context;
        this.parts = Multipart.multipart(context)
            .withBufferSize(8000) // 8kb
            .withHeadersSizeLimit(8000) // 8kb
            .forBlockingIO(body);
    }

    public Map<String, Object> parse() throws IOException {
        try {
            while (parts.hasNext()) {
                parseToken();
            }
            return parsedRequest;
        } finally {
            parts.close();
        }
    }

        private void parseToken() throws IOException {
            var partToken = parts.next();
            var partTokenType = partToken.getType();

            switch (partTokenType) {
                case PART:
                    var part = (BlockingIOAdapter.Part) partToken;
                    var headers= part.getHeaders();

                    if (MultipartUtils.isFormField(headers, context)) {
                        var fieldName = MultipartUtils.getFieldName(headers);
                        var fieldValue = readFormParameterValue(part.getPartBody(), headers);
                        parsedRequest.put(fieldName, fieldValue);
                    } else {
                        var fieldName = MultipartUtils.getFieldName(headers);
                        var files = (ArrayList<JsonObject>) parsedRequest.get(fieldName);
                        if (files == null) {
                            files = new ArrayList<JsonObject>();
                        }
                        var fileName = MultipartUtils.getFileName(headers);
                        files.add(new JsonObject().put(fileName,
                                new String(Base64.getEncoder().encode(part.getPartBody().readAllBytes()))));
                        parsedRequest.put(fieldName, files);
                    }
                    break;
                case NESTED_START:
                    // A marker to keep track of nested multipart and it gives access to the headers...
                    // val headers = partToken.headers
                    // Demo does not support nested multipart
                    break;
                case NESTED_END:
                    break;
                default:
                    // Impossible
                    break;
            }
        }

    private String readFormParameterValue(InputStream inputStream, Map<String, List<String>> headers) {
        try {
            return IOUtils.inputStreamAsString(inputStream, MultipartUtils.getCharEncoding(headers));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read the form parameter value", e);
        }
    }
}

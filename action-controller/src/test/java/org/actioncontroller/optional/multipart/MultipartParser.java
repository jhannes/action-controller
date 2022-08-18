package org.actioncontroller.optional.multipart;

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
            BlockingIOAdapter.ParserToken partToken = parts.next();
            BlockingIOAdapter.ParserToken.Type partTokenType = partToken.getType();

            switch (partTokenType) {
                case PART:
                    BlockingIOAdapter.Part part = (BlockingIOAdapter.Part) partToken;
                    Map<String, List<String>> headers= part.getHeaders();

                    if (MultipartUtils.isFormField(headers, context)) {
                        String fieldName = MultipartUtils.getFieldName(headers);
                        String fieldValue = readFormParameterValue(part.getPartBody(), headers);
                        parsedRequest.put(fieldName, fieldValue);
                    } else {
                        String fieldName = MultipartUtils.getFieldName(headers);
                        ArrayList<JsonObject> files = (ArrayList<JsonObject>) parsedRequest.get(fieldName);
                        if (files == null) {
                            files = new ArrayList<>();
                        }
                        String fileName = MultipartUtils.getFileName(headers);
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

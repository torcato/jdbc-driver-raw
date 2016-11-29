package raw.jdbc.rawclient;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import raw.jdbc.rawclient.requests.*;

public class RawRestClient {

    private static final String JSON_CONTENT = "application/json";
    private static final int HTTP_OK = 200;

    private static Logger logger = Logger.getLogger(RawRestClient.class.getName());
    private static ObjectMapper mapper = new ObjectMapper();

    private HttpClient client = HttpClientBuilder.create().build();
    private String executerUrl;
    private TokenResponse credentials;

    public RawRestClient(String executor, TokenResponse token) {
        this.credentials = token;
        this.executerUrl = executor;
    }

    /**
     * Gets a token using password authentication to an oauth2 server
     *
     * @param credentials
     * @return Return token response
     * @throws IOException
     */
    public static TokenResponse getPasswdGrantToken(String authUrl, PasswordTokenRequest credentials) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(authUrl);

        String json = mapper.writeValueAsString(credentials);
        logger.fine("sending request for token, json: " + json);
        StringEntity entity = new StringEntity(json, "UTF-8");

        entity.setContentType("application/json");
        post.setEntity(entity);

        HttpResponse response = client.execute(post);
        int code = response.getStatusLine().getStatusCode();
        if (code != HTTP_OK) {
            throw new IOException("HTTP request using clientId, secret for token failed with code " + code);
        }

        String jsonStr = getJsonContent(response);
        TokenResponse oauthToken = mapper.readValue(jsonStr, TokenResponse.class);
        return oauthToken;
    }

    public String getVersion() throws IOException {
        HttpResponse response = doGet("/version");
        String contentType = response.getEntity().getContentType().getValue();
        assert (contentType.contains("text/plain"));
        String data = EntityUtils.toString(response.getEntity());
        return data;
    }

    public String[] getSchemas() throws IOException {
        SchemasResponse data = doGet("/schemas", SchemasResponse.class);
        return data.schemas;
    }

    public int asyncQueryStart(String query) throws IOException {
        AsyncQueryRequest request = new AsyncQueryRequest();
        request.query = query;
        AsyncQueryResponse data = doJsonPost("/async-query-start", request, AsyncQueryResponse.class);
        return data.queryId;
    }

    public QueryStartResponse queryStart(String query, int resultsPerPage) throws IOException {
        QueryStartRequest request = new QueryStartRequest();
        request.query = query;
        request.resultsPerPage = resultsPerPage;
        QueryStartResponse response = doJsonPost("/query-start", request, QueryStartResponse.class);
        return response;
    }
    public void queryClose(String token) throws IOException {
        QueryCloseRequest request = new QueryCloseRequest();
        request.token = token;
        String json = mapper.writeValueAsString(request);
        HttpResponse response = doJsonPost("/query-close", json);
        String content = EntityUtils.toString(response.getEntity());
        logger.warning("query-close response: " + content);
    }

    private static String getJsonContent(HttpResponse response) throws IOException {
        String contentType = response.getEntity().getContentType().getValue();
        if (!contentType.contains(JSON_CONTENT)) {
            throw new RuntimeException(
                    "Expected content with '" + JSON_CONTENT + "' but got instead " + contentType);
        }
        return EntityUtils.toString(response.getEntity());
    }

    private <T> T getObjFromResponse(HttpResponse response, Class<T> tClass) throws IOException {
        String json = getJsonContent(response);
        T obj = mapper.readValue(json, tClass);
        return obj;
    }

    private HttpResponse doJsonPost(String path, String json) throws IOException {
        HttpPost post = new HttpPost(executerUrl + path);
        post.setHeader("Authorization", "Bearer " + credentials.access_token);
        StringEntity entity = new StringEntity(json, "UTF-8");
        entity.setContentType("application/json");
        post.setEntity(entity);
        HttpResponse response = client.execute(post);
        int code = response.getStatusLine().getStatusCode();
        logger.fine("Finished get to path: '" + path + "' with code: " + code);
        return response;
    }

    private <T> T doJsonPost(String path, Object request, Class<T> responseClass) throws IOException {
        String json = mapper.writeValueAsString(request);
        HttpResponse response = doJsonPost(path, json);
        int code = response.getStatusLine().getStatusCode();
        if (code != HTTP_OK) {
            String content = EntityUtils.toString(response.getEntity());
            logger.warning("Request to " + path + " response: " + content);
            throw new IOException("json post request to " + path + "failed with code " + code);
        }
        return getObjFromResponse(response, responseClass);
    }

    private HttpResponse doGet(String path) throws IOException {
        String url = executerUrl + path;
        logger.fine("sending get to " + url);
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + credentials.access_token);
        HttpResponse response = client.execute(get);
        int code = response.getStatusLine().getStatusCode();
        logger.fine("Finished get to path: " + path + "with code: " + code);
        return response;
    }

    private <T> T doGet(String path, Class<T> tClass) throws IOException {
        HttpResponse response = doGet(path);
        String json = getJsonContent(response);
        return mapper.readValue(json, tClass);
    }

}
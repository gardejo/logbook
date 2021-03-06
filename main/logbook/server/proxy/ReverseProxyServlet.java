package logbook.server.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import logbook.config.AppConfig;
import logbook.data.Data;
import logbook.data.DataType;
import logbook.data.UndefinedData;
import logbook.data.context.GlobalContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.ProxyConfiguration;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.swt.widgets.Display;

/**
 * リバースプロキシ
 *
 */
public final class ReverseProxyServlet extends ProxyServlet {

    /** ライブラリバグ対応 (HttpRequest#queryを上書きする) */
    private static final Field QUERY_FIELD = getDeclaredField(HttpRequest.class, "query");

    /*
     * リモートホストがローカルループバックアドレス以外の場合400を返し通信しない
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException {
        if (AppConfig.get().isAllowOnlyFromLocalhost()
                && !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
            response.setStatus(400);
            return;
        }
        super.service(request, response);
    }

    /*
     * Hop-by-Hop ヘッダーを除去します
     */
    @Override
    protected void customizeProxyRequest(Request proxyRequest, HttpServletRequest request) {
        proxyRequest.onRequestContent(new RequestContentListener(request));

        // Hop-by-Hop ヘッダーを除去します
        proxyRequest.header(HttpHeader.VIA, null);
        proxyRequest.header(HttpHeader.X_FORWARDED_FOR, null);
        proxyRequest.header(HttpHeader.X_FORWARDED_PROTO, null);
        proxyRequest.header(HttpHeader.X_FORWARDED_HOST, null);
        proxyRequest.header(HttpHeader.X_FORWARDED_SERVER, null);
        proxyRequest.header("Origin", null);

        String queryString = ((org.eclipse.jetty.server.Request) request).getQueryString();
        fixQueryString(proxyRequest, queryString);

        super.customizeProxyRequest(proxyRequest, request);
    }

    @Override
    protected String filterResponseHeader(HttpServletRequest request,
            String headerName,
            String headerValue)
    {
        // Content Encoding を取得する
        if (headerName.compareToIgnoreCase("Content-Encoding") == 0) {
            request.setAttribute(Filter.CONTENT_ENCODING, headerValue);
        }
        return super.filterResponseHeader(request, headerName, headerValue);
    }

    /*
     * レスポンスが帰ってきた
     */
    @Override
    protected void onResponseContent(HttpServletRequest request, HttpServletResponse response,
            Response proxyResponse,
            byte[] buffer, int offset, int length) throws IOException {

        // フィルタークラスで必要かどうかを判別後、必要であれば内容をキャプチャする
        // 注意: 1回のリクエストで複数回の応答が帰ってくるので全ての応答をキャプチャする必要がある
        if (Filter.isNeed(request.getServerName(), response.getContentType())) {
            ByteArrayOutputStream stream = (ByteArrayOutputStream) request.getAttribute(Filter.RESPONSE_BODY);
            if (stream == null) {
                stream = new ByteArrayOutputStream();
                request.setAttribute(Filter.RESPONSE_BODY, stream);
            }
            // ストリームに書き込む
            stream.write(buffer, offset, length);
        }

        super.onResponseContent(request, response, proxyResponse, buffer, offset, length);
    }

    /*
     * レスポンスが完了した
     */
    @Override
    protected void onResponseSuccess(HttpServletRequest request, HttpServletResponse response,
            Response proxyResponse) {

        if (Filter.isNeed(request.getServerName(), response.getContentType())) {
            byte[] postField = (byte[]) request.getAttribute(Filter.REQUEST_BODY);
            ByteArrayOutputStream stream = (ByteArrayOutputStream) request.getAttribute(Filter.RESPONSE_BODY);
            if (stream != null) {
                byte[] responseBody = stream.toByteArray();

                // 圧縮されていたら解凍する
                String contentEncoding = (String) request.getAttribute(Filter.CONTENT_ENCODING);
                if ((contentEncoding != null) && contentEncoding.equals("gzip")) {
                    try {
                        responseBody = IOUtils.toByteArray(new GZIPInputStream(new ByteArrayInputStream(responseBody)));
                    } catch (IOException e) {
                        //
                    }
                }

                final UndefinedData rawData = new UndefinedData(request.getRequestURL().toString(),
                        request.getRequestURI(), postField, responseBody);
                final String serverName = request.getServerName();

                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        // 統計データベース(http://kancolle-db.net/)に送信する
                        DatabaseClient.send(rawData);

                        // キャプチャしたバイト配列は何のデータかを決定する
                        Data data = rawData.toDefinedData();
                        if (data.getDataType() != DataType.UNDEFINED) {
                            // 定義済みのデータの場合にキューに追加する
                            GlobalContext.updateContext(data);

                            // サーバー名が不明の場合、サーバー名をセットする
                            if (!Filter.isServerDetected()) {
                                Filter.setServerName(serverName);
                            }
                        }
                    }
                });
            }
        }
        super.onResponseSuccess(request, response, proxyResponse);
    }

    /*
     * HttpClientを作成する
     */
    @Override
    protected HttpClient newHttpClient() {
        HttpClient client = super.newHttpClient();
        // プロキシを設定する
        if (AppConfig.get().isUseProxy()) {
            // ポート
            int port = AppConfig.get().getProxyPort();
            // ホスト
            String host = AppConfig.get().getProxyHost();
            // 設定する
            client.setProxyConfiguration(new ProxyConfiguration(host, port));
        }
        return client;
    }

    /**
     * private フィールドを取得する
     * @param clazz クラス
     * @param string フィールド名
     * @return フィールドオブジェクト
     */
    private static <T> Field getDeclaredField(Class<T> clazz, String string) {
        try {
            Field field = clazz.getDeclaredField(string);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * ライブラリのバグを修正します<br>
     * URLにマルチバイト文字が含まれている場合にURLが正しく組み立てられないバグを修正します
     * </p>
     */
    private static void fixQueryString(Request proxyRequest, String queryString) {
        if (!StringUtils.isEmpty(queryString)) {
            if (proxyRequest instanceof HttpRequest) {
                try {
                    QUERY_FIELD.set(proxyRequest, queryString);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}